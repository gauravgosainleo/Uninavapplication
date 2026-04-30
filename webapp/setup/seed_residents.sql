-- Sample residents roster. Replace these rows with your real
-- Uninav Heights residents before going live -- this table is the
-- source of truth for auto-allocating house numbers on signup.
--
-- Match is case-insensitive on the `name` column, so trailing spaces
-- and capitalisation don't matter; punctuation does.
--
-- You can also import a CSV instead via phpMyAdmin -> residents -> Import.

INSERT INTO residents (name, house_number, society) VALUES
    ('Sample Resident One',   'A-101', 'Uninav Heights'),
    ('Sample Resident Two',   'A-102', 'Uninav Heights'),
    ('Sample Resident Three', 'B-201', 'Uninav Heights'),
    ('Sample Resident Four',  'B-202', 'Uninav Heights'),
    ('Sample Resident Five',  'C-301', 'Uninav Heights')
ON DUPLICATE KEY UPDATE house_number = VALUES(house_number);
