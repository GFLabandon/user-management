INSERT INTO departments (name) VALUES ('Engineering'), ('Product'), ('Operations');
INSERT INTO roles (name) VALUES ('Administrator'), ('Editor'), ('Viewer');

INSERT INTO users (username, note, dept_id) VALUES
    ('Alex Chen', 'Backend engineer', 1),
    ('Jamie Lin', 'Product designer', 2),
    ('Morgan Wu', 'Operations specialist', 3);

INSERT INTO user_roles (user_id, role_id) VALUES
    (1, 1),
    (1, 2),
    (2, 2),
    (3, 3);
