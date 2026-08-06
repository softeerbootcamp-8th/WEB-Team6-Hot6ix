package com.hot6ix.upbid.domain.auction.entity;

import com.hot6ix.upbid.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "auction_participants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auction_participants_room_user",
                columnNames = {"auction_room_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auction_participant_id")
    private Long auctionParticipantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auction_room_id")
    private AuctionRoom auctionRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "agreed_at")
    private LocalDateTime agreedAt;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Builder
    private AuctionParticipant(AuctionRoom auctionRoom, User user) {
        this.auctionRoom = auctionRoom;
        this.user = user;
    }
}
