UPDATE store_commerce_profiles
SET accept_online_orders = TRUE
WHERE (commerce_enabled = TRUE OR published_to_marketplace = TRUE)
  AND accept_online_orders = FALSE;
