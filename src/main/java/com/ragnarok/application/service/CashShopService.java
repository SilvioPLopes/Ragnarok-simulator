package com.ragnarok.application.service;

import com.ragnarok.domain.exception.GameException;
import com.ragnarok.infrastructure.antifraude.FraudClient;
import com.ragnarok.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CashShopService {

    private final CashShopItemRepository cashShopItemRepository;
    private final AccountRepository accountRepository;
    private final PlayerRepository playerRepository;
    private final PlayerItemRepository playerItemRepository;
    private final ItemRepository itemRepository;
    private final FraudClient fraudClient;

    public CashShopService(CashShopItemRepository cashShopItemRepository,
                           AccountRepository accountRepository,
                           PlayerRepository playerRepository,
                           PlayerItemRepository playerItemRepository,
                           ItemRepository itemRepository,
                           FraudClient fraudClient) {
        this.cashShopItemRepository = cashShopItemRepository;
        this.accountRepository = accountRepository;
        this.playerRepository = playerRepository;
        this.playerItemRepository = playerItemRepository;
        this.itemRepository = itemRepository;
        this.fraudClient = fraudClient;
    }

    public List<CashShopItemEntity> listItems() {
        return cashShopItemRepository.findByActiveTrue();
    }

    @Transactional
    public void buy(Long accountId, Long cashShopItemId, Long playerId) {
        CashShopItemEntity shopItem = cashShopItemRepository.findById(cashShopItemId)
                .orElseThrow(() -> new IllegalArgumentException("Item nao encontrado na loja cash"));
        if (!shopItem.isActive()) {
            throw new IllegalArgumentException("Item nao esta disponivel na loja");
        }
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Conta nao encontrada"));
        if (account.getCashPoints() < shopItem.getCashPrice()) {
            throw new IllegalStateException("Cash Points insuficientes");
        }

        long totalActive = cashShopItemRepository.countByActiveTrue();
        FraudClient.FraudDecision decision = fraudClient.checkMarketPurchase(
                playerId, shopItem.getItemId(), 1L, totalActive);
        if (decision.isBlocked()) {
            throw new GameException("Compra bloqueada pelo sistema antifraude");
        }

        account.setCashPoints(account.getCashPoints() - shopItem.getCashPrice());
        accountRepository.save(account);

        PlayerEntity player = playerRepository.findById(playerId).orElseThrow();
        ItemEntity item = itemRepository.findById(shopItem.getItemId()).orElseThrow();

        List<PlayerItemEntity> existing = playerItemRepository.findByPlayerIdAndItemId(playerId, item.getId());
        if (!existing.isEmpty()) {
            existing.get(0).setAmount(existing.get(0).getAmount() + 1);
            playerItemRepository.save(existing.get(0));
        } else {
            PlayerItemEntity pi = new PlayerItemEntity();
            pi.setPlayer(player);
            pi.setItem(item);
            pi.setAmount(1);
            pi.setEquipped(false);
            playerItemRepository.save(pi);
        }
    }
}
