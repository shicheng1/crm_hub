-- 密码升级迁移：将明文密码升级为 BCrypt 加密
-- 执行方式：mysql -u root -p order_approval < db/migration/V2__bcrypt_passwords.sql

USE order_approval;

-- 将明文密码 '123456' 升级为 BCrypt（仅对未加密的密码生效）
-- BCrypt hash 以 $2a$ 或 $2b$ 开头，以此判断是否已加密
UPDATE sys_user 
SET password = CASE username
    WHEN 'admin'  THEN '$2a$10$UufSIYKvR.buOeLGoK5Mc.kwOvigsT2rqh0T5dSynDz0Vdto9My6q'
    WHEN 'user1'  THEN '$2a$10$08mG7m0DNBQNd1MHhWQ8aeXttg52Oz0LCocS2CqRDZcgHusj/rIoq'
    WHEN 'user2'  THEN '$2a$10$8Sh2GPpUxk7yPN3oID3/uudZtYE4TLtTb3oQkoXNOFQbKBJ00iHeu'
    ELSE password
END
WHERE password NOT LIKE '$2a$%' AND password NOT LIKE '$2b$%';

-- 验证：所有密码都应该是 BCrypt 格式
SELECT id, username, 
    CASE 
        WHEN password LIKE '$2a$%' OR password LIKE '$2b$%' THEN 'BCrypt OK'
        ELSE 'PLAINTEXT - NEEDS UPGRADE'
    END AS password_status
FROM sys_user;
