#!/usr/bin/env python3
"""JDK生成物の整合性検査。レシピの実Codec確認はGameTestで別に実施する。"""
import hashlib
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
        expected_item_hashes = {
            'sulfur_crystal': 'efa8fcc73df7b604d24873724863fdf63c095a9b070909b6aea960f2b2e9f878',
            'venesite': 'fdca1d0288fdab901ed56641e6fe5a1d2d1eac496ddda283a25897472364bcd1',
            'pressure_alloy_blend': '6da9750428d1dccfb305ed061b51a765ab82bc68786caac79dc9f01ba1a5e45c',
            'pressure_alloy_ingot': '94848b0b8d901d873b9f084d888e77bb52d554d97a911ec3cdf3306a8972da73',
            'portable_life_support': '2bfef4a4d6db3d121defa839c51e71636a70c2bd591155f8428d55df2d7c84ef',
            'pressure_helmet': 'c022c45350537a92c0dce6b96ca7baba22fb7f18cf07900699d5a541790773cb',
            'pressure_chestplate': 'bbd369716a8154c2de174f216e45e1b4e1f953c26a5a62fbc37b71ce4b49fcbb',
            'pressure_leggings': '25586deb2b1ec976a027e7ed650d3fe3ed92808decd62a9adb877ed63c00387e',
            'pressure_boots': '9c863e60deef9d411c5c7d6e9ceb22fca88dfb5220ed1a64260e343dce0d223b',
            'acid_condensate_bucket': '45ba7f8dbc5f1f306e0487ee386587a9aabebe938c21b59015ce85bf13e44646',
        }
        for name, expected in expected_item_hashes.items():
            path = f'assets/venusmod/textures/item/{name}.png'
            assert path in generated, f'Missing fixed item texture: {name}'
            assert hashlib.sha256(generated[path]).hexdigest() == expected, f'Item texture changed unexpectedly: {name}'
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
        print(f'Phase2 resources: {len(data)} JSON, {sum(key.endswith(".png") for key in generated)} PNG; 10 item textures hash-locked; reproducible; no cyclic entry recipe; Create optional.')

if __name__ == '__main__': main()
