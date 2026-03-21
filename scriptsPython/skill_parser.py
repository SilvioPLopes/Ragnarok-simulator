"""
rAthena Skill Parser
======================
Lê db/skill_db.yml e db/skill_tree.yml e gera:
  - skills.sql       → tabela de todas as skills
  - skill_tree.sql   → skills por classe com level máximo e pré-requisitos

Uso: python3 skill_parser.py

Dependências:
    pip install requests pyyaml --break-system-packages
"""

import requests
import yaml

GITHUB_RAW  = "https://raw.githubusercontent.com/rathena/rathena/master"
SKILL_DB    = f"{GITHUB_RAW}/db/re/skill_db.yml"
SKILL_TREE  = f"{GITHUB_RAW}/db/re/skill_tree.yml"


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
    return None


def parsear_skills(content):
    """Retorna lista de dicts: id, aegis_name, name, type, script"""
    data = yaml.safe_load(content)
    if not data or "Body" not in data:
        return []

    skills = []
    for skill in data["Body"]:
        skill_id   = skill.get("Id")
        aegis_name = skill.get("Name", "")       # AegisName no rAthena
        name       = skill.get("Description", aegis_name)
        skill_type = skill.get("SkillInfo", "")   # Passive, etc
        script     = skill.get("Script", None)

        if not skill_id:
            continue

        # Normaliza type
        if isinstance(skill_type, list):
            skill_type = ",".join(str(s) for s in skill_type)
        elif skill_type is None:
            skill_type = ""

        skills.append({
            "id":         int(skill_id),
            "aegis_name": str(aegis_name),
            "name":       str(name),
            "type":       str(skill_type),
            "script":     str(script) if script else None,
        })

    return skills


def parsear_skill_tree(content):
    """Retorna lista de dicts: job_class, skill_id, max_level, prereq_skill_id, prereq_level"""
    data = yaml.safe_load(content)
    if not data or "Body" not in data:
        return []

    entries = []
    for job in data["Body"]:
        job_class = job.get("Job")
        if not job_class:
            continue

        for skill in job.get("Tree", []):
            skill_id  = skill.get("Name")       # campo correto no skill_tree.yml
            max_level = skill.get("MaxLevel", 1)
            prereqs   = skill.get("Requires", []) or []

            if not skill_id:
                continue

            # Sem pré-requisito
            if not prereqs:
                entries.append({
                    "job_class":    str(job_class),
                    "skill_id":     str(skill_id),
                    "max_level":    int(max_level),
                    "prereq_skill": None,
                    "prereq_level": None,
                })
            else:
                for prereq in prereqs:
                    entries.append({
                        "job_class":    str(job_class),
                        "skill_id":     str(skill_id),
                        "max_level":    int(max_level),
                        "prereq_skill": str(prereq.get("Name")),
                        "prereq_level": int(prereq.get("Level", 1)),
                    })

    return entries


def gerar_skills_sql(skills):
    with open("skills.sql", "w", encoding="utf-8") as f:
        f.write("-- Gerado automaticamente pelo skill_parser.py\n\n")
        f.write("CREATE TABLE IF NOT EXISTS skills (\n")
        f.write("    id          BIGINT PRIMARY KEY,\n")
        f.write("    aegis_name  VARCHAR(100) NOT NULL,\n")
        f.write("    name        VARCHAR(150),\n")
        f.write("    type        VARCHAR(100),\n")
        f.write("    script      TEXT\n")
        f.write(");\n\n")
        f.write("ALTER TABLE skills ADD COLUMN IF NOT EXISTS script TEXT;\n\n")

        if skills:
            f.write("INSERT INTO skills (id, aegis_name, name, type, script) VALUES\n")
            linhas = []
            for s in skills:
                aegis  = s["aegis_name"].replace("'", "''")
                name   = s["name"].replace("'", "''")
                stype  = s["type"].replace("'", "''")
                script = s["script"].replace("'", "''") if s["script"] else None
                script_val = f"'{script}'" if script else "NULL"
                linhas.append(f"  ({s['id']}, '{aegis}', '{name}', '{stype}', {script_val})")
            f.write(",\n".join(linhas))
            f.write("\nON CONFLICT (id) DO UPDATE SET\n")
            f.write("    name   = EXCLUDED.name,\n")
            f.write("    type   = EXCLUDED.type,\n")
            f.write("    script = EXCLUDED.script;\n")

    print(f"  skills.sql gerado — {len(skills)} skills")


def gerar_skill_tree_sql(entries):
    with open("skill_tree.sql", "w", encoding="utf-8") as f:
        f.write("-- Gerado automaticamente pelo skill_parser.py\n\n")
        f.write("CREATE TABLE IF NOT EXISTS skill_tree (\n")
        f.write("    id              SERIAL PRIMARY KEY,\n")
        f.write("    job_class       VARCHAR(50) NOT NULL,\n")
        f.write("    skill_id        VARCHAR(100) NOT NULL,\n")
        f.write("    max_level       INTEGER NOT NULL DEFAULT 1,\n")
        f.write("    prereq_skill    VARCHAR(100),\n")
        f.write("    prereq_level    INTEGER,\n")
        f.write("    UNIQUE (job_class, skill_id, prereq_skill)\n")
        f.write(");\n\n")

        if entries:
            f.write("INSERT INTO skill_tree (job_class, skill_id, max_level, prereq_skill, prereq_level) VALUES\n")
            linhas = []
            for e in entries:
                prereq_skill = f"'{e['prereq_skill']}'" if e["prereq_skill"] else "NULL"
                prereq_level = str(e["prereq_level"]) if e["prereq_level"] else "NULL"
                linhas.append(
                    f"  ('{e['job_class']}', '{e['skill_id']}', {e['max_level']}, {prereq_skill}, {prereq_level})"
                )
            f.write(",\n".join(linhas))
            f.write("\nON CONFLICT (job_class, skill_id, prereq_skill) DO UPDATE SET\n")
            f.write("    max_level    = EXCLUDED.max_level,\n")
            f.write("    prereq_level = EXCLUDED.prereq_level;\n")

    print(f"  skill_tree.sql gerado — {len(entries)} entradas")


def main():
    print("rAthena Skill Parser\n")

    print("Carregando skill_db...")
    skill_content = baixar_yaml(SKILL_DB)
    if not skill_content:
        print("Falha ao baixar skill_db.yml")
        return

    print("Carregando skill_tree...")
    tree_content = baixar_yaml(SKILL_TREE)
    if not tree_content:
        print("Falha ao baixar skill_tree.yml")
        return

    print("\nParseando...")
    skills  = parsear_skills(skill_content)
    entries = parsear_skill_tree(tree_content)

    print(f"  {len(skills)} skills encontradas")
    print(f"  {len(entries)} entradas de skill_tree encontradas")

    print("\nGerando SQLs...")
    gerar_skills_sql(skills)
    gerar_skill_tree_sql(entries)

    print("\nSanidade — primeiras classes na skill_tree:")
    classes = sorted(set(e["job_class"] for e in entries))
    for c in classes[:10]:
        qtd = len([e for e in entries if e["job_class"] == c])
        print(f"  {c}: {qtd} skills")


if __name__ == "__main__":
    main()