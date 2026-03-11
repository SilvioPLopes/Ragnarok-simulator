"""
rAthena Map Index Parser
=========================
Uso: python3 map_parser.py
"""

import requests

MAP_INDEX_URL = "https://raw.githubusercontent.com/rathena/rathena/master/db/map_index.txt"

def main():
    print("🔍 Baixando map_index.txt...")
    response = requests.get(MAP_INDEX_URL)

    if response.status_code != 200:
        print(f"❌ Erro ao baixar: {response.status_code}")
        return

    mapas = []
    for linha in response.text.splitlines():
        linha = linha.strip()
        if not linha or linha.startswith("//"):
            continue
        partes = linha.split()
        if len(partes) >= 1:
            mapas.append(partes[0])

    print(f"✅ {len(mapas)} mapas encontrados")

    with open("maps.sql", "w", encoding="utf-8") as f:
        f.write("-- Gerado automaticamente pelo map_parser.py\n\n")
        f.write("INSERT INTO maps (map_id, name) VALUES\n")
        linhas_sql = [f"  ('{m}', '{m}')" for m in mapas]
        f.write(",\n".join(linhas_sql))
        f.write("\nON CONFLICT (map_id) DO NOTHING;\n")

    print("✅ maps.sql gerado")

if __name__ == "__main__":
    main()