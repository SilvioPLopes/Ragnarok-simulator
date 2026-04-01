package com.ragnarok.infrastructure.antifraude;

import org.springframework.stereotype.Component;

/**
 * Anti-fraud client stub. In a real environment this would call an external
 * fraud-detection service. For now it provides a simple pass-through implementation
 * that always returns FALLBACK_APPROVED so the game stays playable.
 */
@Component
public class FraudClient {

    public enum FraudDecision {
        APPROVED(false),
        FALLBACK_APPROVED(false),
        REVIEW(false),
        BLOCKED(true);

        private final boolean blocked;

        FraudDecision(boolean blocked) {
            this.blocked = blocked;
        }

        public boolean isBlocked() {
            return blocked;
        }
    }

    /**
     * Checks whether a login attempt should be allowed.
     *
     * @param accountId     the account performing login
     * @param ipAddress     the IP address of the request
     * @param country       ISO-3166 country code inferred from the IP
     * @param emailVerified whether the account e-mail is verified
     * @param ageVerified   whether the account age is verified
     * @return the fraud decision; callers must block when {@link FraudDecision#isBlocked()} is true
     */
    public FraudDecision checkLogin(Long accountId, String ipAddress, String country,
                                    boolean emailVerified, boolean ageVerified) {
        return FraudDecision.FALLBACK_APPROVED;
    }

    /**
     * Asynchronously notifies the fraud system of a new registration so it can
     * build a risk profile for the account.
     *
     * @param accountId     newly created account id
     * @param emailVerified whether the e-mail was verified at registration time
     * @param ageVerified   whether the age was verified at registration time
     * @param referralCode  optional referral code used during registration
     */
    public void syncRegistrationAsync(Long accountId, boolean emailVerified,
                                      boolean ageVerified, String referralCode) {
        // no-op stub — would publish to a message queue in production
    }

    /**
     * Checks whether an item trade between players should be allowed.
     *
     * @param senderPlayerId  the player sending/selling the item
     * @param itemId          the item's catalog id
     * @param playerItemUuid  the specific PlayerItem UUID as string
     * @param requestedZenny  the zenny amount requested in exchange
     */
    public FraudDecision checkItemTrade(Long senderPlayerId, Long itemId,
                                        String playerItemUuid, Long requestedZenny) {
        return FraudDecision.FALLBACK_APPROVED;
    }

    /**
     * Checks whether a market purchase should be allowed.
     *
     * @param buyerPlayerId      the player making the purchase
     * @param itemId             the item's catalog id
     * @param quantity           quantity being purchased
     * @param totalActiveListings total active listings for this item (market activity signal)
     */
    public FraudDecision checkMarketPurchase(Long buyerPlayerId, Long itemId,
                                             Long quantity, Long totalActiveListings) {
        return FraudDecision.FALLBACK_APPROVED;
    }
}
