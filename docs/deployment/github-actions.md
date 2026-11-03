# 자동 배포

`main`의 CI가 통과하면 Java와 OCR 이미지를 GHCR에 게시한다.
AWS Systems Manager Run Command를 통해 EC2가 해당 이미지를 받아 실행한다.
배포에는 커밋의 전체 SHA를 사용하며, 더 최신 `main`이 있으면 이전 배포는 건너뛴다.

## 동작 범위

- PR에서는 기존 CI만 실행한다.
- `main`의 CI 통과 후 두 이미지를 `linux/amd64`로 빌드한다. 이전 빌드 레이어를 재사용한다.
- 이미지 이름은 `ghcr.io/hirelink-team/hirelink-backend`와 `ghcr.io/hirelink-team/hirelink-ocr`다.
- DB 데이터, 서버 환경변수, 인증서 파일은 이미지에 넣지 않는다.
- `AUTO_DEPLOY_ENABLED=true`일 때만 EC2에 배포 명령을 보낸다.
- GitHub는 OIDC로 임시 AWS 자격 증명을 받는다. SSH 키나 장기 AWS 접근 키는 등록하지 않는다.
- 배포는 한 번에 하나씩 실행한다. 새 배포가 진행 중인 배포를 취소하지 않는다.
- 이미지 다운로드와 설정 검사가 끝난 뒤 컨테이너를 교체한다. 교체 중에는 잠시 요청이 실패할 수 있다.
- 컨테이너 상태와 HTTPS `/health` 확인까지 통과해야 배포 성공으로 처리한다.

최초 EC2 준비는 [EC2 배포](./ec2.md)를 따른다. 저장소는 `/home/ubuntu/HIRELINK-Backend`에 있어야 한다.
기존 `/etc/hirelink/backend.env`, 인증서, Caddy 인증서 볼륨은 계속 사용한다.

## 1. GHCR 패키지 설정

GitHub Actions가 처음 이미지를 게시하면 패키지는 기본적으로 비공개다.
조직의 Packages에서 `hirelink-backend`, `hirelink-ocr` 각각의 Package settings를 열어 공개로 전환한다.
이 작업에는 패키지 관리자 권한이 필요하다. 조직의 패키지 생성·공개 정책도 확인한다.

두 패키지를 모두 공개한 다음 EC2에서 인증 없이 내려받을 수 있는지 확인한다.
패키지 공개와 AWS 연결이 끝나기 전에는 `AUTO_DEPLOY_ENABLED`를 켜지 않는다.

## 2. EC2가 원격 명령을 받을 권한

1. IAM에서 신뢰할 서비스가 EC2인 전용 역할을 만든다.
2. AWS 관리형 정책 `AmazonSSMManagedInstanceCore`를 연결한다.
3. EC2의 보안 메뉴에서 이 역할을 인스턴스에 연결한다. 기존 역할이 있다면 교체 전에 용도를 확인한다.
4. SSM Agent가 실행 중이며 Systems Manager에서 인스턴스가 Online으로 표시되는지 확인한다.

EC2에서 AWS Systems Manager에 대한 HTTPS 아웃바운드 연결이 필요하다.
SSH 인바운드 허용 범위를 넓히지 않는다.

## 3. GitHub가 배포를 요청할 권한

IAM에 GitHub OIDC 공급자가 없다면 다음 값으로 등록한다.

- Provider URL: `https://token.actions.githubusercontent.com`
- Audience: `sts.amazonaws.com`

GitHub 배포 전용 IAM 역할을 만들고 아래 신뢰 정책을 사용한다.
`ACCOUNT_ID`는 사용하는 AWS 계정 ID로 바꾼다. `main`만 이 역할을 사용할 수 있도록 제한한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:HIRELINK-Team/HIRELINK-Backend:ref:refs/heads/main"
      }
    }
  }]
}
```

이 역할에는 다음 권한 정책을 연결한다. `ACCOUNT_ID`와 `INSTANCE_ID`를 대상 값으로 바꾼다.
실행 명령 전송은 지정한 EC2 한 대와 `AWS-RunShellScript` 문서에 한정한다.
`GetCommandInvocation`은 개별 리소스 ARN 제한을 지원하지 않아 `Resource`에 `*`를 사용한다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:ACCOUNT_ID:instance/INSTANCE_ID",
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
      ]
    },
    {
      "Effect": "Allow",
      "Action": "ssm:GetCommandInvocation",
      "Resource": "*"
    }
  ]
}
```

이 권한으로 대상 EC2에서 셸 명령을 실행할 수 있으므로, 저장소의 `main` 및 워크플로 수정 권한을 관리한다.

## 4. 저장소 설정과 최초 실행

저장소 Settings → Secrets and variables → Actions에서 아래 항목을 등록한다.

| 구분 | 이름 | 값 |
| --- | --- | --- |
| Secret | `AWS_DEPLOY_ROLE_ARN` | GitHub 배포 전용 IAM 역할의 ARN |
| Secret | `EC2_INSTANCE_ID` | 대상 EC2 인스턴스 ID |
| Variable | `AUTO_DEPLOY_ENABLED` | 준비가 끝나면 `true` |

역할 ARN과 인스턴스 ID는 인증키가 아니지만 실행 로그에서 가려지도록 Secret으로 등록한다.
DB 비밀번호, JWT 키, 외부 서비스 키, SSH 개인키는 GitHub에 등록하지 않는다.

1. 배포 워크플로가 포함된 `main`의 CI와 이미지 게시 성공을 확인한다.
2. 두 GHCR 패키지를 공개하고 EC2의 SSM 연결을 확인한다.
3. 위 Secret 두 개를 등록하고 `AUTO_DEPLOY_ENABLED`를 `true`로 설정한다.
4. Actions → Deploy → Run workflow에서 `main`으로 실행한다. 수동 실행도 해당 커밋의 CI 통과 여부를 검사한다.
5. 배포 성공과 HTTPS 접속을 확인한다.

이후 `main`에 머지하면 CI 통과 후 자동 배포한다. EC2가 중지된 상태에서는 배포할 수 없다.

## 실패 시 확인

- CI 또는 이미지 게시 실패: 실행 중인 서비스는 교체하지 않는다.
- 이미지 다운로드 실패: 기존 실행 설정과 컨테이너를 유지한다.
- 컨테이너 교체 후 실패: 워크플로는 실패로 표시한다. EC2에서 상태를 확인한다.
- Flyway는 앱 시작 시 실행된다. DB 변경의 역호환성을 확인하지 않고 이전 이미지로 자동 복구하지 않는다.
- 자동 배포를 멈추려면 `AUTO_DEPLOY_ENABLED`를 `false`로 설정한다. 실행 중인 서비스는 계속 동작한다.
- 배포 로그에는 단계별 결과만 출력한다. 장애 분석 시에도 환경변수 파일이나 전체 컨테이너 로그를 공개하지 않는다.

## 공식 문서

- [GitHub Container registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)
- [GitHub Actions의 AWS OIDC 연결](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)
- [AWS Systems Manager Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/execute-remote-commands.html)
