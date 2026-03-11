"""
Ragnarok DB Migration Runner
==============================
Roda todos os SQLs de migração em ordem no banco PostgreSQL.

Configuração via variáveis de ambiente ou edite as constantes abaixo.

Uso:
    python3 migrate.py

Dependências:
    pip install psycopg2-binary --break-system-packages
"""

import psycopg2
import os

# ── Configuração do banco ─────────────────────────────────────────────────────
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "ragnarok_db")
DB_USER = os.getenv("DB_USER", "postgres")
DB_PASS = os.getenv("DB_PASS", "postgre")

# ── Ordem das migrações ───────────────────────────────────────────────────────
# Coloque os arquivos na ordem correta respeitando dependências de FK
MIGRATIONS = [
    "maps.sql",            # tabela maps (sem FK)
    "map_portals_v2.sql",  # portais (sem FK com monsters)
    "map_monsters.sql",    # spawns por mapa (FK: monsters)
    "monster_drops.sql",   # drops (FK: monsters + items)
]

# ── Runner ────────────────────────────────────────────────────────────────────
def main():
    print("Ragnarok DB Migration Runner")
    print(f"Conectando em {DB_USER}@{DB_HOST}:{DB_PORT}/{DB_NAME}\n")

    try:
        conn = psycopg2.connect(
            host=DB_HOST, port=DB_PORT,
            dbname=DB_NAME, user=DB_USER, password=DB_PASS
        )
        conn.autocommit = False
        cur = conn.cursor()
    except Exception as e:
        print(f"ERRO ao conectar: {e}")
        return

    pasta = os.path.dirname(os.path.abspath(__file__))

    for arquivo in MIGRATIONS:
        caminho = os.path.join(pasta, arquivo)

        if not os.path.exists(caminho):
            print(f"  AVISO: {arquivo} não encontrado, pulando.")
            continue

        print(f"Rodando {arquivo}...")
        try:
            with open(caminho, "r", encoding="utf-8") as f:
                sql = f.read()

            cur.execute(sql)
            conn.commit()
            print(f"  OK")
        except Exception as e:
            conn.rollback()
            print(f"  ERRO: {e}")
            print(f"  Rollback feito. Continuando proxima migracao...")

    cur.close()
    conn.close()
    print("\nMigracoes concluidas.")


if __name__ == "__main__":
    main()