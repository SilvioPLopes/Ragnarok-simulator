package com.ragnarok.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MonsterDrop {

    // Removemos ID e anotações de banco. O domínio não precisa saber chave primária de drop.

    private Item item;   // Trocamos 'String name' pelo Objeto Item para ter acesso aos dados
    private Double rate; // Mantemos a taxa
}