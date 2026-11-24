-- Read-only registration transition counts. Returns aggregate counts only.
WITH identities AS (
  SELECT user_id, LOWER(TRIM(email)) AS email FROM users WHERE NULLIF(TRIM(email),'') IS NOT NULL
  UNION
  SELECT user_id, LOWER(TRIM(provider_email)) FROM user_social_accounts WHERE NULLIF(TRIM(provider_email),'') IS NOT NULL
), shared AS (
  SELECT email FROM identities GROUP BY email HAVING COUNT(DISTINCT user_id)>1
)
SELECT COUNT(*) AS existing_users,
       SUM(EXISTS(SELECT 1 FROM user_social_accounts a WHERE a.user_id=u.user_id AND a.provider='NAVER')) AS linked_naver,
       SUM(NOT EXISTS(SELECT 1 FROM identities i WHERE i.user_id=u.user_id)) AS missing_email,
       SUM(EXISTS(SELECT 1 FROM identities i JOIN shared s ON s.email=i.email WHERE i.user_id=u.user_id)) AS shared_email
FROM users u;
