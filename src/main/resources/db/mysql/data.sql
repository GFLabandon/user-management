INSERT IGNORE INTO departments (id, name) VALUES
    (1, 'Engineering'),
    (2, 'Product'),
    (3, 'Operations');

INSERT IGNORE INTO roles (id, name) VALUES
    (1, 'Administrator'),
    (2, 'Editor'),
    (3, 'Viewer');

INSERT IGNORE INTO users (id, username, note, dept_id) VALUES
    (1, 'Alex Chen', 'Backend engineer', 1),
    (2, 'Jamie Lin', 'Product designer', 2),
    (3, 'Morgan Wu', 'Operations specialist', 3);

INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
    (1, 1),
    (1, 2),
    (2, 2),
    (3, 3);
