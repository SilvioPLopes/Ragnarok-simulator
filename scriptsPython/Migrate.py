"""
Ragnarok DB Migration Runner
==============================
Roda todos os SQLs de migração em ordem no banco PostgreSQL.
Seguro para reexecutar — todos os SQLs usam:
  - CREATE TABLE IF NOT EXISTS
  - ON CONFLICT DO UPDATE (upsert)

Configuração via variáveis de ambiente ou edite as constantes abaixo.

Uso:
    python3 Migrate.py
    python3 Migrate.py --force    # ignora verificação de tabelas existentes

Dependências:
    pip install psycopg2-binary --break-system-packages
"""

import psycopg2
import os
import sys

# ── Configuração do banco ─────────────────────────────────────────────────────
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "ragnarok_db")
DB_USER = os.getenv("DB_USER", "postgres")
DB_PASS = os.getenv("DB_PASS", "postgre")

# ── Ordem das migrações ───────────────────────────────────────────────────────
# Respeita dependências de FK — não altere a ordem
MIGRATIONS = [
    ("maps.sql",            "maps"),
    ("map_portals_v2.sql",  "map_portals"),   # sempre usar v2, nunca map_portals.sql
    ("map_monsters.sql",    "map_monsters"),
    ("monster_drops.sql",   "monster_drops"),
    ("skills.sql",          "skills"),
    ("skill_tree.sql",      "skill_tree"),
]


def tabela_existe(cur, tabela):
    cur.execute("""
        SELECT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = %s
        )
    """, (tabela,))
    return cur.fetchone()[0]


def tabela_tem_dados(cur, tabela):
    try:
        cur.execute(f"SELECT COUNT(*) FROM {tabela}")
        return cur.fetchone()[0] > 0
    except Exception:
        return False


def main():
    force = "--force" in sys.argv

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

    for arquivo, tabela in MIGRATIONS:
        caminho = os.path.join(pasta, arquivo)

        if not os.path.exists(caminho):
            print(f"  AVISO: {arquivo} não encontrado, pulando.")
            continue

        # Proteção: se tabela existe e tem dados, pula (a menos que --force)
        if not force and tabela_existe(cur, tabela) and tabela_tem_dados(cur, tabela):
            cur.execute(f"SELECT COUNT(*) FROM {tabela}")
            count = cur.fetchone()[0]
            print(f"  PULANDO {arquivo} — tabela '{tabela}' já tem {count} registros. Use --force para sobrescrever.")
            continue

        print(f"Rodando {arquivo}...")
        try:
            with open(caminho, "r", encoding="utf-8") as f:
                sql = f.read()

            cur.execute(sql)
            conn.commit()

            if tabela_existe(cur, tabela):
                cur.execute(f"SELECT COUNT(*) FROM {tabela}")
                count = cur.fetchone()[0]
                print(f"  OK — {count} registros em '{tabela}'")
            else:
                print(f"  OK")
        except Exception as e:
            conn.rollback()
            print(f"  ERRO: {e}")
            print(f"  Rollback feito. Continuando próxima migração...")

    cur.close()
    conn.close()
    print("\nMigrações concluídas.")


if __name__ == "__main__":
    main()