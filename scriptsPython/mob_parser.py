"""
rAthena Mob Spawn Parser
=========================
Lê arquivos de spawn de npc/re/mobs/ e npc/pre-re/mobs/
Gera SQL para a tabela map_monsters

Uso: python3 mob_parser.py
"""

import requests
import re
import csv

GITHUB_API = "https://api.github.com/repos/rathena/rathena/contents"
GITHUB_RAW = "https://raw.githubusercontent.com/rathena/rathena/master"

# Preferimos re/ sobre pre-re/ quando ambos existem
MOB_DIRS = [
    "npc/re/mobs/fields",
    "npc/re/mobs/cities",
    "npc/re/mobs/dungeons",
    "npc/re/mobs/other",
    "npc/pre-re/mobs/fields",
    "npc/pre-re/mobs/cities",
    "npc/pre-re/mobs/dungeons",
    "npc/pre-re/mobs/other",
]

# Formato: mapa,x,y[,rx,ry] <TAB> monster <TAB> Nome <TAB> id,quantidade[,respawn...]
# Exemplo: prt_fild08,0,0	monster	Poring	1002,20,5000
MOB_PATTERN = re.compile(
    r'^(\w+),(\d+),(\d+)(?:,\d+,\d+)?\s+monster\s+(.+?)\s+(\d+),(\d+)',
    re.MULTILINE
)


def listar_arquivos(diretorio):
    url = f"{GITHUB_API}/{diretorio}"
    response = requests.get(url)
    if response.status_code != 200:
        print(f"  ⚠ Nao conseguiu listar {diretorio}: {response.status_code}")
        return []

    arquivos = []
    for item in response.json():
        if item["type"] == "file" and item["name"].endswith(".txt"):
            arquivos.append(item["path"])
        elif item["type"] == "dir":
            arquivos.extend(listar_arquivos(item["path"]))
    return arquivos


def baixar_arquivo(path):
    url = f"{GITHUB_RAW}/{path}"
    response = requests.get(url)
    if response.status_code == 200:
        return response.text
    print(f"  ⚠ Erro ao baixar {path}: {response.status_code}")
    return ""


def main():
    print("rAthena Mob Spawn Parser\n")

    todos_spawns = []
    # chave: (mapa, monster_id) → pega o maior amount entre re e pre-re
    vistos = {}

    for diretorio in MOB_DIRS:
        eh_re = "/re/" in diretorio
        print(f"Listando {diretorio}...")
        arquivos = listar_arquivos(diretorio)
        print(f"   {len(arquivos)} arquivo(s)")

        for path in arquivos:
            conteudo = baixar_arquivo(path)
            for match in MOB_PATTERN.finditer(conteudo):
                mapa        = match.group(1)
                monster_id  = int(match.group(5))
                amount      = int(match.group(6))

                chave = (mapa, monster_id)

                # Prefere re/ sobre pre-re/; entre duplicatas do mesmo tipo, soma
                if chave not in vistos:
                    vistos[chave] = {
                        "mapa": mapa,
                        "monster_id": monster_id,
                        "amount": amount,
                        "eh_re": eh_re,
                    }
                else:
                    entrada = vistos[chave]
                    # Se já temos re e chegou pre-re, ignora
                    if entrada["eh_re"] and not eh_re:
                        continue
                    # Se é o mesmo tipo (re+re ou pre+pre), soma os amounts
                    entrada["amount"] += amount

    todos_spawns = list(vistos.values())
    print(f"\nTotal: {len(todos_spawns)} entradas de spawn")

    # CSV
    with open("mob_spawns.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=["mapa", "monster_id", "amount", "eh_re"])
        writer.writeheader()
        writer.writerows(todos_spawns)
    print("mob_spawns.csv gerado")

    # SQL
    with open("map_monsters.sql", "w", encoding="utf-8") as f:
        f.write("-- Gerado automaticamente pelo mob_parser.py\n\n")
        f.write("CREATE TABLE IF NOT EXISTS map_monsters (\n")
        f.write("    id          SERIAL PRIMARY KEY,\n")
        f.write("    map_id      VARCHAR(50) NOT NULL,\n")
        f.write("    monster_id  BIGINT NOT NULL,\n")
        f.write("    amount      INTEGER NOT NULL DEFAULT 1,\n")
        f.write("    UNIQUE (map_id, monster_id)\n")
        f.write(");\n\n")
        f.write("INSERT INTO map_monsters (map_id, monster_id, amount)\n")
        f.write("SELECT t.map_id, t.monster_id::BIGINT, t.amount\n")
        f.write("FROM (VALUES\n")

        linhas = [
            f"  ('{s['mapa']}', {s['monster_id']}, {s['amount']})"
            for s in todos_spawns
        ]
        f.write(",\n".join(linhas))
        f.write("\n) AS t(map_id, monster_id, amount)\n")
        f.write("WHERE EXISTS (SELECT 1 FROM monsters WHERE id = t.monster_id::BIGINT)\n")
        f.write("ON CONFLICT (map_id, monster_id) DO UPDATE SET amount = EXCLUDED.amount;\n")

    print("map_monsters.sql gerado")

    # Verifica prt_fild08 como sanidade
    print("\nSanidade - prt_fild08:")
    for s in todos_spawns:
        if s["mapa"] == "prt_fild08":
            print(f"  Monster {s['monster_id']} x{s['amount']}")


if __name__ == "__main__":
    main()