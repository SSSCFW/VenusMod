#!/usr/bin/env python3
"""静的なデータ検査。MinecraftのCodec読込・Mixin適用・プレイテストの代替ではない。"""
from collections import deque
import gzip
import json
from pathlib import Path
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'

class NBTReader:
    def __init__(self, data): self.data, self.offset = data, 0
    def take(self, n):
        value = self.data[self.offset:self.offset+n]
        assert len(value) == n, 'Truncated NBT'
        self.offset += n
        return value
    def number(self, fmt): return struct.unpack('>'+fmt, self.take(struct.calcsize('>'+fmt)))[0]
    def text(self): return self.take(self.number('H')).decode('utf-8')
    def value(self, kind):
        if kind == 2: return self.number('h')
        if kind == 3: return self.number('i')
        if kind == 8: return self.text()
        if kind == 9:
            child, count = self.number('B'), self.number('i')
            assert count >= 0
            return [self.value(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while (child := self.number('B')):
                key = self.text()
                assert key not in result, 'Duplicate NBT key'
                result[key] = self.value(child)
            return result
        raise AssertionError(f'Unsupported NBT tag {kind}')

def main():
    subprocess.run(['python3', str(ROOT/'tools/validate_phase2.py')], check=True)
    workspace = tempfile.TemporaryDirectory(prefix='venus-assets-')
    work = Path(workspace.name)
    classes, assets = work/'classes', work/'resources'
    classes.mkdir()
    subprocess.run(['javac','-d',str(classes),str(ROOT/'tools/java/VenusAssetGenerator.java')],check=True)
    command = ['java','-Djava.awt.headless=true','-cp',str(classes),'VenusAssetGenerator',str(assets)]
    subprocess.run(command,check=True)
    documents = {p.relative_to(RES).as_posix(): json.loads(p.read_text(encoding='utf-8'))
                 for p in RES.rglob('*.json')}
    def data(folder, name): return documents[f'data/venusmod/{folder}/{name}.json']
    assert data('dimension', 'venus')['generator']['settings'] == 'venusmod:venus'
    assert data('dimension', 'venus')['generator']['biome_source']['biome'] == 'venusmod:venus_wastes'
    dimension = data('dimension_type', 'venus')
    assert dimension['monster_spawn_block_light_limit'] == 0
    light_provider = dimension['monster_spawn_light_level']
    assert light_provider == {
        'type': 'minecraft:uniform',
        'min_inclusive': 0,
        'max_inclusive': 7,
    }, '1.21.1 DimensionType IntProvider must keep min/max at the provider root (no nested value object)'
    assert dimension['fixed_time'] == 18000 and dimension['coordinate_scale'] == 1
    assert data('worldgen/noise_settings', 'venus')['noise']['height'] == dimension['height']
    biome = data('worldgen/biome', 'venus_wastes')
    assert {entry['type'] for entry in biome['spawners']['monster']} == {
        'venusmod:venus_'+name for name in ('zombie','skeleton','creeper','spider','enderman')}
    assert biome['features'][6] == ['venusmod:ore_nickel', 'venusmod:sulfur_ore', 'venusmod:venesite_ore']
    ore = data('worldgen/configured_feature', 'ore_nickel')
    assert {target['state']['Name'] for target in ore['config']['targets']} == {
        'venusmod:nickel_ore','venusmod:deepslate_nickel_ore'}
    assert data('worldgen/placed_feature', 'ore_nickel')['feature'] == 'venusmod:ore_nickel'
    assert data('worldgen/structure', 'venus_citadel')['start_pool'] == 'venusmod:venus_citadel/start'
    assert data('worldgen/structure_set', 'venus_citadel')['placement']['spacing'] > data('worldgen/structure_set', 'venus_citadel')['placement']['separation']
    pool = data('worldgen/template_pool', 'venus_citadel/start')
    assert pool['elements'][0]['element']['location'] == 'venusmod:venus_citadel'
    assert data('tags/worldgen/biome/has_structure', 'venus_citadel')['values'] == ['venusmod:venus_wastes']
    forge_recipe = documents['data/venusmod/recipe/venus_blade_forge.json']
    assert {condition['modid'] for condition in forge_recipe['neoforge:conditions']} == {'create','slashblade'}
    assert forge_recipe['result']['id'] == 'venusmod:venus_blade_forge'
    machine_values = documents['data/venusmod/tags/block/machines.json']['values']
    assert {'id':'venusmod:venus_blade_forge','required':False} in machine_values
    forge_model = documents['assets/venusmod/models/block/venus_blade_forge.json']
    assert forge_model['ambientocclusion'] is False
    for element in forge_model['elements']:
        assert all(0 <= value <= 16 for value in element['from'] + element['to'])
        assert all('cullface' not in face for face in element['faces'].values())
    treasury_png = (RES/'assets/venusmod/textures/item/king_treasury.png').read_bytes()
    assert treasury_png[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', treasury_png[16:24]) == (16,16)
    treasury_rules = (ROOT/'src/main/java/dev/ssscfw/venusmod/treasury/TreasuryRules.java').read_text(encoding='utf-8')
    assert 'MAX_STACKS = 10_000' in treasury_rules
    assert 'MAX_LOGICAL_STACK = 1_028' in treasury_rules
    assert 'PAGE_SIZE = 54' in treasury_rules

    for name, size in [('block/venus_portal',(16,256)),('item/venus_core',(16,16))]:
        content = (assets/f'assets/venusmod/textures/{name}.png').read_bytes()
        assert content[:8] == b'\x89PNG\r\n\x1a\n'
        assert struct.unpack('>II', content[16:24]) == size
    portal_model = documents['assets/venusmod/models/block/venus_portal.json']
    assert portal_model['render_type'] == 'minecraft:translucent'
    for lang in ('ja_jp','en_us'):
        d = documents[f'assets/venusmod/lang/{lang}.json']
        for key in ('entity.venusmod.venus_guardian','block.venusmod.venus_portal','item.venusmod.venus_core',
                    'message.venusmod.dimension_missing','message.venusmod.no_safe_exit','message.venusmod.entity_too_large',
                    'item.venusmod.king_treasury','container.venusmod.king_treasury',
                    'message.venusmod.king_treasury_auto_on','message.venusmod.king_treasury_auto_off',
                    'gui.venusmod.king_treasury.page','gui.venusmod.king_treasury.total'):
            assert key in d
    assert 'VenusDimensionContent.register(modBus);' in (ROOT/'src/main/java/dev/ssscfw/venusmod/VenusMod.java').read_text(encoding='utf-8')
    assert 'config="venusmod.mixins.json"' in (RES/'META-INF/neoforge.mods.toml').read_text(encoding='utf-8')
    for name in documents['venusmod.mixins.json']['mixins']:
        assert (ROOT/f'src/main/java/dev/ssscfw/venusmod/mixin/{name}.java').is_file()

    workflows = sorted(p.name for p in (ROOT/'.github/workflows').glob('*') if p.is_file())
    assert workflows == ['build-1.21.1.yml'], f'Unexpected workflow files: {workflows}'

    reader = NBTReader(gzip.decompress((assets/'data/venusmod/structure/venus_citadel.nbt').read_bytes()))
    assert reader.number('B') == 10
    assert reader.text() == ''
    nbt = reader.value(10)
    assert reader.offset == len(reader.data)
    size, palette = nbt['size'], nbt['palette']
    assert size == [31,16,51] and not nbt['entities']
    blocks = {}
    for block in nbt['blocks']:
        pos = tuple(block['pos'])
        assert pos not in blocks and all(0 <= p < bound for p,bound in zip(pos,size))
        assert 0 <= block['state'] < len(palette)
        blocks[pos] = block
    def name(pos): return palette[blocks[pos]['state']]['Name'] if pos in blocks else 'minecraft:air'
    assert sum(name(pos) == 'venusmod:guardian_altar' for pos in blocks) == 1
    assert sum(name(pos) == 'minecraft:spawner' for pos in blocks) == 2
    assert sum(name(pos) == 'minecraft:chest' for pos in blocks) == 2
    for pos, block in blocks.items():
        if 'nbt' in block and 'LootTable' in block['nbt']:
            assert block['nbt']['LootTable'] == 'venusmod:chests/venus_citadel'
        if name(pos) == 'minecraft:spawner':
            assert block['nbt']['SpawnData']['entity']['id'].startswith('venusmod:venus_')
    allowed = {'minecraft:air','minecraft:iron_door','minecraft:lever'}
    def walkable(x,z):
        return 0 <= x < 31 and 4 <= z < 51 and name((x,4,z)) in allowed and name((x,5,z)) in allowed and name((x,3,z)) != 'minecraft:air'
    visited, queue = {(15,4)}, deque([(15,4)])
    while queue:
        x,z = queue.popleft()
        for p in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if p not in visited and walkable(*p): visited.add(p); queue.append(p)
    for goal in ((6,10),(24,10),(6,23),(24,23),(15,40)):
        assert goal in visited, f'Unreachable room: {goal}'
    assert name((14,5,31)) == 'minecraft:lever' and name((14,5,32)) == 'minecraft:polished_blackstone_bricks'
    assert name((15,4,32)) == 'minecraft:iron_door' and name((15,5,32)) == 'minecraft:iron_door'
    generated_files = [assets/'assets/venusmod/textures/block/venus_portal.png', assets/'assets/venusmod/textures/item/venus_core.png', assets/'data/venusmod/structure/venus_citadel.nbt']
    before = [p.read_bytes() for p in generated_files]
    subprocess.run(command,check=True)
    assert before == [p.read_bytes() for p in generated_files], 'Asset generation is not reproducible'
    print(f'PASS: {len(documents)} JSON files; PNG sizes; registry links; {len(blocks)} NBT blocks; four rooms and boss arena connected; reproducible assets')
    workspace.cleanup()
    print('NOT TESTED: Java/NeoForge compilation, Minecraft Codec loading, mixin application, graphics, portal travel, redstone and combat in-game.')

if __name__ == '__main__': main()
