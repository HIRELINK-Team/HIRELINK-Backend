INSERT INTO business_type (id, name) VALUES
    (1, 'FOOD_CAFE'),
    (2, 'RETAIL'),
    (3, 'OFFICE_EVENT_TOURISM'),
    (4, 'INTERPRETATION_TRANSLATION'),
    (5, 'DUTY_FREE'),
    (6, 'MAJOR_RELATED'),
    (7, 'INTERNSHIP'),
    (8, 'OTHER');

SELECT setval(pg_get_serial_sequence('business_type', 'id'), (SELECT MAX(id) FROM business_type));

INSERT INTO priority_criterion (id, criterion_name) VALUES
    (1, 'WORK_TIME'),
    (2, 'WAGE'),
    (3, 'DISTANCE'),
    (4, 'BUSINESS_TYPE');

SELECT setval(pg_get_serial_sequence('priority_criterion', 'id'), (SELECT MAX(id) FROM priority_criterion));
