-- V4: corrige 7 mapas sem portal de saída (soft-lock fix)
-- Cada INSERT usa ON CONFLICT DO NOTHING para ser idempotente.
-- A tabela map_portals não tem unique constraint definida, então usamos
-- a estratégia de verificar antes de inserir via NOT EXISTS.

INSERT INTO map_portals (map_from, x_from, y_from, map_to, x_to, y_to)
SELECT * FROM (VALUES
  -- alde_gld
  ('alde_gld',   183, 198, 'turbo_room',  100,  62),
  -- jupe_ele_r
  ('jupe_ele_r',  49,  30, 'jupe_gate',    49,  55),
  -- moc_fild20 (13 entradas → 13 saídas)
  ('moc_fild20',  36, 177, 'morocc',      299, 207),
  ('moc_fild20',  36, 177, 'moc_fild11',  377, 197),
  ('moc_fild20', 349, 179, 'moc_fild21',   26, 196),
  ('moc_fild20', 349, 179, 'moc_fild22',   32, 196),
  ('moc_fild20', 210, 342, 'moc_fild01',  101,  16),
  ('moc_fild20',  36, 177, 'moc_fild07',  380, 201),
  ('moc_fild20', 197,  24, 'moc_fild11',  189, 360),
  ('moc_fild20', 349, 179, 'moc_fild13',   32, 171),
  ('moc_fild20', 197,  24, 'moc_fild16',  124, 381),
  ('moc_fild20', 197,  24, 'moc_fild16',  333, 380),
  ('moc_fild20', 209, 333, 'prt_fild09',  246,  17),
  ('moc_fild20', 209, 333, 'prt_fild09',   95,  19),
  ('moc_fild20', 209, 333, 'prt_fild10',  263,  22),
  -- prt_lib_q
  ('prt_lib_q',   89,  43, 'prt_q',       155, 358),
  -- ra_san01
  ('ra_san01',   140,  19, 'ra_san02',    213, 280),
  ('ra_san01',   140,  19, 'ra_san03',    123, 283),
  ('ra_san01',   140,  19, 'ra_san04',    119, 104),
  -- tha_t06
  ('tha_t06',    206,  11, 'tha_t05',     185, 235),
  -- tha_t12
  ('tha_t12',    115,  16, 'thana_step',  181,  15)
) AS v(map_from, x_from, y_from, map_to, x_to, y_to)
WHERE NOT EXISTS (
  SELECT 1 FROM map_portals p
  WHERE p.map_from = v.map_from
    AND p.map_to   = v.map_to
);
