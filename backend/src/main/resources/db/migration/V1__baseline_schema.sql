-- Flyway 도입 시점(#96)의 스키마 전체다.
--
-- 손으로 쓴 것이 아니라, 빈 MySQL 8.4.9 에 이 커밋을 ddl-auto: create 로 띄워 Hibernate 가
-- 만든 스키마를 mysqldump --no-data 로 뽑았다. 운영 RDS 도 같은 Hibernate 가 만든 것이라
-- 이래야 실제 운영 스키마와 어긋나지 않는다.
--
-- FKrst179ldcyphob14adwxf1u2g 같은 제약 이름은 Hibernate 가 지은 것인데, 운영에 그 이름으로
-- 이미 있으므로 바꾸지 않는다. 바꾸면 운영과 새로 만드는 DB 의 이름이 갈린다.

CREATE TABLE `users` (
  `created_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `nickname` varchar(20) DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `provider_id` varchar(100) DEFAULT NULL,
  `email` varchar(254) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `profile_image_url` text,
  `provider` enum('DEV','KAKAO') DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_users_provider_provider_id` (`provider`,`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `seller_profiles` (
  `created_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `seller_profile_id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `store_name` varchar(30) NOT NULL,
  `store_phone_number` varchar(30) DEFAULT NULL,
  `store_description` varchar(100) DEFAULT NULL,
  `sns_url` text,
  `store_image_url` text,
  PRIMARY KEY (`seller_profile_id`),
  UNIQUE KEY `UK2264dwvu9q06u7388998fl3he` (`user_id`),
  CONSTRAINT `FKcpr5ibp9058g7a9u58wh7xf2y` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `products` (
  `created_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `product_id` bigint NOT NULL AUTO_INCREMENT,
  `seller_profile_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(30) NOT NULL,
  `description` varchar(100) DEFAULT NULL,
  `image_url` text,
  `reference_url` text,
  PRIMARY KEY (`product_id`),
  KEY `FKkysx1vsiuq47yg7rdic09t4ic` (`seller_profile_id`),
  CONSTRAINT `FKkysx1vsiuq47yg7rdic09t4ic` FOREIGN KEY (`seller_profile_id`) REFERENCES `seller_profiles` (`seller_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auction_rooms` (
  `soft_close_extend_seconds` int DEFAULT NULL,
  `soft_close_trigger_seconds` int DEFAULT NULL,
  `auction_room_id` bigint NOT NULL AUTO_INCREMENT,
  `bid_increment` bigint NOT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `seller_profile_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `share_code` varchar(32) DEFAULT NULL,
  `name` varchar(100) DEFAULT NULL,
  `cover_image_url` text,
  `description` text,
  `live_url` text,
  `status` enum('BEFORE','CLOSED','OPEN') DEFAULT NULL,
  PRIMARY KEY (`auction_room_id`),
  UNIQUE KEY `UK1oktff5kjhbsbiv6g9y1jyrm9` (`share_code`),
  KEY `FKsc0ubkl3293oh30iweayl1cv3` (`seller_profile_id`),
  CONSTRAINT `FKsc0ubkl3293oh30iweayl1cv3` FOREIGN KEY (`seller_profile_id`) REFERENCES `seller_profiles` (`seller_profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auction_items` (
  `total_extension_seconds` int NOT NULL,
  `auction_item_id` bigint NOT NULL AUTO_INCREMENT,
  `auction_room_id` bigint NOT NULL,
  `bid_increment` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `current_price` bigint NOT NULL,
  `end_at` datetime(6) DEFAULT NULL,
  `leader_user_id` bigint DEFAULT NULL,
  `original_end_at` datetime(6) DEFAULT NULL,
  `product_id` bigint NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `starting_price` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','IN_PROGRESS','READY','SOLD') NOT NULL,
  PRIMARY KEY (`auction_item_id`),
  KEY `idx_auction_items_product_id` (`product_id`),
  KEY `FKrst179ldcyphob14adwxf1u2g` (`auction_room_id`),
  KEY `FKslfi56grvq3x2xw8m2nki9hsc` (`leader_user_id`),
  CONSTRAINT `FKgro5p10s3rnsuja3tux62fe87` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`),
  CONSTRAINT `FKrst179ldcyphob14adwxf1u2g` FOREIGN KEY (`auction_room_id`) REFERENCES `auction_rooms` (`auction_room_id`),
  CONSTRAINT `FKslfi56grvq3x2xw8m2nki9hsc` FOREIGN KEY (`leader_user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auction_participants` (
  `agreed_at` datetime(6) DEFAULT NULL,
  `auction_participant_id` bigint NOT NULL AUTO_INCREMENT,
  `auction_room_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `terms_version` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`auction_participant_id`),
  UNIQUE KEY `uk_auction_participants_room_user` (`auction_room_id`,`user_id`),
  KEY `FK9jnbjd50w43dg3vtc4b1th1fw` (`user_id`),
  CONSTRAINT `FK2w5pmk5vqkmisvsaybg9smb40` FOREIGN KEY (`auction_room_id`) REFERENCES `auction_rooms` (`auction_room_id`),
  CONSTRAINT `FK9jnbjd50w43dg3vtc4b1th1fw` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `bids` (
  `accepted_at` datetime(6) NOT NULL,
  `amount` bigint NOT NULL,
  `auction_item_id` bigint NOT NULL,
  `bid_id` bigint NOT NULL AUTO_INCREMENT,
  `bidder_user_id` bigint NOT NULL,
  PRIMARY KEY (`bid_id`),
  UNIQUE KEY `uk_bids_item_amount` (`auction_item_id`,`amount`),
  KEY `idx_bids_item_bidder_amount` (`auction_item_id`,`bidder_user_id`,`amount`),
  KEY `FK1yrwjn1o8ke0emi88lg99r4oa` (`bidder_user_id`),
  CONSTRAINT `FK1yrwjn1o8ke0emi88lg99r4oa` FOREIGN KEY (`bidder_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKpqaen91gv0u0970xman60be5t` FOREIGN KEY (`auction_item_id`) REFERENCES `auction_items` (`auction_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `deal_candidates` (
  `candidate_rank` int NOT NULL,
  `auction_item_id` bigint NOT NULL,
  `bid_amount` bigint NOT NULL,
  `bidder_user_id` bigint NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `deal_candidate_id` bigint NOT NULL AUTO_INCREMENT,
  `failed_at` datetime(6) DEFAULT NULL,
  `status` enum('COMPLETED','FAILED','WAITING') DEFAULT NULL,
  PRIMARY KEY (`deal_candidate_id`),
  KEY `FKrvntyq0hsh26bfttirb36kr3k` (`auction_item_id`),
  KEY `FKcpgkqfwhx8e3h4ut2pmvt3w6p` (`bidder_user_id`),
  CONSTRAINT `FKcpgkqfwhx8e3h4ut2pmvt3w6p` FOREIGN KEY (`bidder_user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `FKrvntyq0hsh26bfttirb36kr3k` FOREIGN KEY (`auction_item_id`) REFERENCES `auction_items` (`auction_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
