-- Explicit demo profile only. Seed empty installations with fictional records.
INSERT INTO departments (name) SELECT '示例信息学院' WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = '示例信息学院');
INSERT INTO departments (name) SELECT '示例工程学院' WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = '示例工程学院');
INSERT INTO departments (name) SELECT '示例人文学院' WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = '示例人文学院');
INSERT INTO counselors (employee_no, name, department_id, employment_status, remark)
SELECT 'DEMO-001', '示例张老师', id, 'ACTIVE', '虚构演示资料' FROM departments
WHERE name = '示例信息学院' AND NOT EXISTS (SELECT 1 FROM counselors WHERE employee_no = 'DEMO-001');
INSERT INTO counselors (employee_no, name, department_id, employment_status, remark)
SELECT 'DEMO-002', '示例李老师', id, 'ACTIVE', '虚构演示资料' FROM departments
WHERE name = '示例工程学院' AND NOT EXISTS (SELECT 1 FROM counselors WHERE employee_no = 'DEMO-002');
INSERT INTO counselors (employee_no, name, department_id, employment_status, remark)
SELECT 'DEMO-003', '示例王老师', id, 'INACTIVE', '虚构停用资料' FROM departments
WHERE name = '示例人文学院' AND NOT EXISTS (SELECT 1 FROM counselors WHERE employee_no = 'DEMO-003');
INSERT INTO counselor_status_history (counselor_id, from_status, to_status, actor)
SELECT c.id, NULL, c.employment_status, 'demo-seed' FROM counselors c
WHERE c.employee_no IN ('DEMO-001', 'DEMO-002', 'DEMO-003')
AND NOT EXISTS (SELECT 1 FROM counselor_status_history h WHERE h.counselor_id = c.id);
