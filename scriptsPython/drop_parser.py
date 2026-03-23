"""
rAthena Monster Drop Parser
=============================
Lê db/re/mob_db.yml e gera monster_drops.sql
Usa APENAS db/re/ para manter consistência com os spawns

Uso: python3 drop_parser.py
"""

import requests
import yaml

GITHUB_RAW = "https://raw.githubusercontent.com/rathena/rathena/master"

MOB_DB_URL   = f"{GITHUB_RAW}/db/re/mob_db.yml"
ITEM_DB_URLS = [
    f"{GITHUB_RAW}/db/re/item_db_usable.yml",
    f"{GITHUB_RAW}/db/re/item_db_equip.yml",
    f"{GITHUB_RAW}/db/re/item_db_etc.yml",
]


def baixar_yaml(url, tentativas=5):
    print(f"  Baixando {url.split('master/')[-1]}...")
    for i in range(tentativas):
        try:
            r = requests.get(url, stream=True, timeout=60)
            if r.status_code != 200:
                print(f"  ERRO {r.status_code}")
                return None
            chunks = []
            for chunk in r.iter_content(chunk_size=8192):
                if chunk:
                    chunks.append(chunk)
            return b"".join(chunks).decode("utf-8")
        except Exception as e:
            print(f"  Tentativa {i+1}/{tentativas} falhou: {e}")
            if i < tentativas - 1:
                import time
                time.sleep(3)
    print("  Falha apos todas as tentativas")
    return None


def parsear_items(urls):
    """Retorna dict: aegis_name -> item_id"""
    mapa = {}
    for url in urls:
        conteudo = baixar_yaml(url)
        if not conteudo:
            continue
        data = yaml.safe_load(conteudo)
        if not data or "Body" not in data:
            continue
        for item in data["Body"]:
            aegis = item.get("AegisName")
            iid   = item.get("Id")
            if aegis and iid:
                mapa[aegis] = int(iid)
    print(f"  {len(mapa)} itens mapeados (AegisName -> Id)")
    return mapa


def parsear_drops(mob_db_content, item_map):
    """Retorna lista de dicts: monster_id, item_id, rate"""
    data = yaml.safe_load(mob_db_content)
    if not data or "Body" not in data:
        print("ERRO: mob_db.yml sem Body")
        return []

    drops = []
    sem_item = set()

    for mob in data["Body"]:
        mob_id = mob.get("Id")
        if not mob_id:
            continue

        for drop in mob.get("Drops", []):
            aegis = drop.get("Item")
            rate  = drop.get("Rate", 0)
            if not aegis or rate <= 0:
                continue

            item_id = item_map.get(aegis)
            if item_id is None:
                sem_item.add(aegis)
                continue

            drops.append({
                "monster_id": int(mob_id),
                "item_id":    item_id,
                "rate":       int(rate),
            })

    if sem_item:
        print(f"  AVISO: {len(sem_item)} itens nao encontrados no item_db (ignorados)")

    return drops


def main():
    print("rAthena Drop Parser\n")

    # 1. Mapeia itens: AegisName -> Id
    print("Carregando item_db...")
    item_map = parsear_items(ITEM_DB_URLS)

    # 2. Baixa mob_db
    print("\nCarregando mob_db...")
    mob_content = baixar_yaml(MOB_DB_URL)
    if not mob_content:
        print("Falha ao baixar mob_db.yml")
        return

    # 3. Parseia drops
    print("Parseando drops...")
    drops_raw = parsear_drops(mob_content, item_map)
    print(f"  {len(drops_raw)} drops brutos encontrados")

    # Deduplica por (monster_id, item_id) mantendo o maior rate
    dedup = {}
    for d in drops_raw:
        chave = (d["monster_id"], d["item_id"])
        if chave not in dedup or d["rate"] > dedup[chave]["rate"]:
            dedup[chave] = d
    drops = list(dedup.values())
    print(f"  {len(drops)} drops apos deduplicacao")

    # 4. Gera SQL
    with open("monster_drops.sql", "w", encoding="utf-8") as f:
        f.write("-- Gerado automaticamente pelo drop_parser.py (db/re)\n\n")
        f.write("CREATE TABLE IF NOT EXISTS monster_drops (\n")
        f.write("    id          SERIAL PRIMARY KEY,\n")
        f.write("    monster_id  BIGINT NOT NULL,\n")
        f.write("    item_id     BIGINT NOT NULL,\n")
        f.write("    rate        DOUBLE PRECISION NOT NULL,\n")  # 0.0-100.0 (rAthena ÷ 100)
        f.write("    UNIQUE (monster_id, item_id)\n")
        f.write(");\n\n")

        if drops:
            # Normaliza escala rAthena (0-10000) → 0.0-100.0 para alinhar com BattleEngine
            f.write("INSERT INTO monster_drops (monster_id, item_id, rate)\n")
            f.write("SELECT t.monster_id::BIGINT, t.item_id::BIGINT, t.rate::DOUBLE PRECISION / 100.0\n")
            f.write("FROM (VALUES\n")
            linhas = [f"  ({d['monster_id']}, {d['item_id']}, {d['rate']})" for d in drops]
            f.write(",\n".join(linhas))
            f.write("\n) AS t(monster_id, item_id, rate)\n")
            f.write("WHERE EXISTS (SELECT 1 FROM monsters WHERE id = t.monster_id::BIGINT)\n")
            f.write("  AND EXISTS (SELECT 1 FROM items WHERE id = t.item_id::BIGINT)\n")
            f.write("ON CONFLICT (monster_id, item_id) DO UPDATE SET rate = EXCLUDED.rate;\n")

    print("monster_drops.sql gerado")

    # Sanidade: Poring (1002)
    print("\nSanidade - Poring (1002):")
    for d in drops:
        if d["monster_id"] == 1002:
            print(f"  item_id={d['item_id']} rate={d['rate']} ({d['rate']/100:.2f}%)")


if __name__ == "__main__":
    main()