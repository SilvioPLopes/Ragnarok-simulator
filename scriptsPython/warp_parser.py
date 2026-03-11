"""
rAthena Warp Parser - versão 2
================================
Inclui npc/re/warps/ que contém os warps de volta dos campos
Uso: python3 warp_parser.py
"""

import requests
import re
import csv
import os

WARP_DIRS = [
    "npc/warps/cities",
    "npc/warps/fields",
    "npc/warps/dungeons",
    "npc/warps/other",
    "npc/re/warps/cities",    # NOVO: contém prontera_fild.txt e outros
    "npc/re/warps/fields",    # NOVO
    "npc/pre-re/warps/cities",
    "npc/pre-re/warps/fields",
]

GITHUB_API = "https://api.github.com/repos/rathena/rathena/contents"
GITHUB_RAW = "https://raw.githubusercontent.com/rathena/rathena/master"

WARP_PATTERN = re.compile(
    r'^(\w+),(\d+),(\d+),\d+\s+warp\s+\S+\s+\d+,\d+,(\w+),(\d+),(\d+)',
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


def parsear_warps(conteudo, origem_arquivo):
    warps = []
    for match in WARP_PATTERN.finditer(conteudo):
        warps.append({
            "mapa_origem":  match.group(1),
            "x_origem":     int(match.group(2)),
            "y_origem":     int(match.group(3)),
            "mapa_destino": match.group(4),
            "x_destino":    int(match.group(5)),
            "y_destino":    int(match.group(6)),
            "arquivo":      os.path.basename(origem_arquivo),
        })
    return warps


def main():
    print("rAthena Warp Parser v2\n")

    todos_warps = []
    vistos = set()  # evita duplicatas entre npc/warps/ e npc/re/warps/

    for diretorio in WARP_DIRS:
        print(f"Listando {diretorio}...")
        arquivos = listar_arquivos(diretorio)
        print(f"   {len(arquivos)} arquivo(s) encontrado(s)")

        for path in arquivos:
            conteudo = baixar_arquivo(path)
            warps = parsear_warps(conteudo, path)
            novos = 0
            for w in warps:
                chave = (w["mapa_origem"], w["x_origem"], w["y_origem"],
                         w["mapa_destino"], w["x_destino"], w["y_destino"])
                if chave not in vistos:
                    vistos.add(chave)
                    todos_warps.append(w)
                    novos += 1
            print(f"   + {os.path.basename(path)}: {novos} warps novos")

    print(f"\nTotal: {len(todos_warps)} warps unicos")

    # CSV
    campos = ["mapa_origem", "x_origem", "y_origem",
              "mapa_destino", "x_destino", "y_destino", "arquivo"]
    with open("warps_v2.csv", "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=campos)
        writer.writeheader()
        writer.writerows(todos_warps)
    print("warps_v2.csv gerado")

    # SQL - usa INSERT com ON CONFLICT para nao duplicar o que ja esta no banco
    with open("map_portals_v2.sql", "w", encoding="utf-8") as f:
        f.write("-- Warps v2 - inclui npc/re/warps\n")
        f.write("-- Roda no DBeaver: File -> Open -> map_portals_v2.sql -> Alt+X\n\n")
        f.write("INSERT INTO map_portals (map_from, x_from, y_from, map_to, x_to, y_to) VALUES\n")
        linhas = []
        for w in todos_warps:
            linhas.append(
                f"  ('{w['mapa_origem']}', {w['x_origem']}, {w['y_origem']}, "
                f"'{w['mapa_destino']}', {w['x_destino']}, {w['y_destino']})"
            )
        f.write(",\n".join(linhas))
        f.write(";\n")
    print("map_portals_v2.sql gerado")

    # Verifica prt_fild05 especificamente
    print("\nVerificando prt_fild05:")
    for w in todos_warps:
        if w["mapa_origem"] == "prt_fild05":
            print(f"  prt_fild05 -> {w['mapa_destino']} (arquivo: {w['arquivo']})")


if __name__ == "__main__":
    main()