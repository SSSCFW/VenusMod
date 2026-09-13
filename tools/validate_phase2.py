#!/usr/bin/env python3
"""JDK生成物の整合性検査。レシピの実Codec確認はGameTestで別に実施する。"""
import json
from pathlib import Path
import struct
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'

def main():
    with tempfile.TemporaryDirectory(prefix='venus-phase2-') as folder:
        work = Path(folder); classes = work/'classes'; out = work/'assets'; classes.mkdir()
        subprocess.run(['javac','-d',str(classes),str(ROOT/'tools/java/Phase2AssetGenerator.java')],check=True)
        command = ['java','-Djava.awt.headless=true','-cp',str(classes),'Phase2AssetGenerator',str(out)]
        subprocess.run(command,check=True)
        generated = {f.relative_to(out).as_posix(): f.read_bytes() for f in out.rglob('*') if f.is_file()}
        assert not any((RES/key).exists() for key in generated), 'Source/generated resource collision'
        data = {path:json.loads(content) for path,content in generated.items() if path.endswith('.json')}
        for path, content in generated.items():
            if path.endswith('.png'):
                assert content[:8] == b'\x89PNG\r\n\x1a\n'
                assert struct.unpack('>II',content[16:24]) == ((64,32) if '/armor/' in path else (16,16))
        suit = data['data/venusmod/tags/item/environment_suit.json']['values']
        assert len(suit) == len(set(suit)) == 4
        assert data['data/venusmod/recipe/atmospheric_condenser.json']['neoforge:conditions'] == [{'type':'neoforge:mod_loaded','modid':'create'}]
        assert data['data/venusmod/loot_table/blocks/atmospheric_condenser.json']['neoforge:conditions'] == [{'type':'neoforge:mod_loaded','modid':'create'}]
        # 入門防護装備はCreate不要。金星資源が生命維持装置に循環依存しない。
        life = data['data/venusmod/recipe/portable_life_support.json']
        assert all(v['item'].startswith('minecraft:') for v in life['key'].values())
        for name in ('pressure_helmet','pressure_chestplate','pressure_leggings','pressure_boots','pressure_alloy_blend'):
            assert 'neoforge:conditions' not in data[f'data/venusmod/recipe/{name}.json']
        for name in ('sulfur_ore','venesite_ore'):
            cf = data[f'data/venusmod/worldgen/configured_feature/{name}.json']
            pf = data[f'data/venusmod/worldgen/placed_feature/{name}.json']
            assert cf['config']['targets'][0]['state']['Name'] == 'venusmod:'+name
            assert pf['feature'] == 'venusmod:'+name
            assert pf['placement'][-1] == {'type':'minecraft:biome'}
            assert 'value' not in pf['placement'][2]['height']
        biome = json.loads((RES/'data/venusmod/worldgen/biome/venus_wastes.json').read_text())
        assert set(biome['features'][6]) == {'venusmod:ore_nickel','venusmod:sulfur_ore','venusmod:venesite_ore'}
        for lang in ('ja_jp','en_us'):
            localized = json.loads((RES/f'assets/venusmod/lang/{lang}.json').read_text(encoding='utf-8'))
            assert all('item.'+name.replace(':','.') in localized for name in suit)
            assert 'block.venusmod.atmospheric_condenser' in localized
            assert 'hud.venusmod.exposure' in localized
        subprocess.run(command,check=True)
        assert generated == {f.relative_to(out).as_posix(): f.read_bytes() for f in out.rglob('*') if f.is_file()}
        print(f'Phase2 resources: {len(data)} JSON, {sum(key.endswith(".png") for key in generated)} PNG; reproducible; no cyclic entry recipe; Create optional.')

if __name__ == '__main__': main()
