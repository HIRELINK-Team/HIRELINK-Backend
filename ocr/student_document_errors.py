from recognition import UnreadableDocument


class UnreadableStudentFields(UnreadableDocument):
    def __init__(self, *fields):
        super().__init__()
        self.fields = list(fields)
