import nbtlib, sys, collections

SHORT = {
 'immersiveengineering:light_engineering':'LE','immersiveengineering:heavy_engineering':'HE',
 'immersiveengineering:rs_engineering':'RS','immersiveengineering:sheetmetal_steel':'SS',
 'immersiveengineering:storage_steel':'ST','immersiveengineering:steel_scaffolding_standard':'SC',
 'immersiveengineering:fluid_pipe':'FP','immersiveengineering:generator':'GE','immersiveengineering:concrete':'CC',
 'immersiveengineering:concrete_tile':'CT','immersiveengineering:radiator':'RD','immersiveengineering:coil_lv':'C1',
 'immersiveengineering:coil_mv':'C2','immersiveengineering:coil_hv':'C3','immersiveengineering:blastbrick_reinforced':'BR',
 'immersiveengineering:slab_sheetmetal_steel':'ss','immersiveengineering:slab_steel_scaffolding_standard':'cs',
 'immersiveengineering:slab_steel_scaffolding_wooden_top':'cw','immersiveengineering:slab_storage_steel':'tS',
 'immersiveengineering:slab_blastbrick_reinforced':'bS','immersiveengineering:stairs_concrete':'c^',
 'immersiveengineering:stairs_steel_scaffolding_standard':'C^','immersiveengineering:steel_fence':'F|',
 'immersiveengineering:sheetmetal_iron':'SI','minecraft:air':'  ','minecraft:hopper':'HO',
}

def load(path):
    d = nbtlib.load(path)
    size = [int(x) for x in d['size']]
    pal = []
    for p in d['palette']:
        props = {k:str(v) for k,v in (dict(p['Properties']) if 'Properties' in p else {}).items()}
        name = str(p['Name'])
        key = SHORT.get(name)
        if key is None:
            key = name.split(':')[-1][:2].upper()
        if props:
            t = props.get('type','')[:1].lower()
            f = props.get('facing','')[:1].lower()
            if t=='top': key = key.strip().upper()+'T'
            if f: key = key[:2]+'>'+f
            key = (key+'  ')[:3]
        pal.append((name,props,key))
    grid = {}
    for b in d['blocks']:
        pos = tuple(int(x) for x in b['pos'])
        grid[pos] = int(b['state'])
    return size, pal, grid

if __name__ == '__main__':
    path = sys.argv[1]
    size, pal, grid = load(path)
    print(path, 'size', size)
    for i,(n,p,k) in enumerate(pal):
        print(f'  [{i}] {k!r} {n} {p}')
    sx, sy, sz = size
    for y in range(sy):
        print(f'--- y={y} (x -> right, z -> down) ---')
        print('    ' + ''.join(f'{x:>4}' for x in range(sx)))
        for z in range(sz):
            row = ''
            for x in range(sx):
                idx = grid.get((x,y,z))
                row += f'{pal[idx][2] if idx is not None else "??":>4}'
            print(f'z={z} ' + row)
