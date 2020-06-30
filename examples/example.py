#!/usr/bin/env python2

import sys

from math import cos, sin, radians


def translate(x, y, vec):
    return [x + vec[0], y + vec[1]]


def rotate(phi, vec):
    phi = radians(phi)
    return [
        vec[0] * cos(phi) - vec[1] * sin(phi),
        vec[0] * sin(phi) + vec[1] * cos(phi),
        ]


def scale(x, y, vec):
    return [x * vec[0], y * vec[1]]


def shear(x, y, vec):
    return [
        vec[0] + y * vec[1],
        vec[0] * x + vec[1],
        ]


def decode_hpgl(hpgl):
    ret = []
    hpgl = hpgl.strip()
    for cmd in hpgl.split(';'):
        if cmd == '':
            continue
        acc = [cmd[:2]]
        if len(cmd) > 2:
            for arg in cmd[2:].split(','):
                acc.append(float(arg))
        ret.append(acc)
    return ret


def encode_hpgl(hpgl):
    ret = ''
    for cmd in hpgl:
        ret += cmd[0]
        ret += ','.join([str(int(x)) for x in cmd[1:]])
        ret += ';'
    return ret + "\n"


def do_transformations(transformations, hpgl):
    def apply_transformation(transformation, vec):
        if transformation[0] == '-Tx':
            return translate(transformation[1], 0, vec)
        elif transformation[0] == '-Ty':
            return translate(0, transformation[1], vec)
        elif transformation[0] == '-T':
            return translate(transformation[1], transformation[1], vec)
        elif transformation[0] == '-R':
            return rotate(transformation[1], vec)
        elif transformation[0] == '-Sx':
            return scale(transformation[1], 1, vec)
        elif transformation[0] == '-Sy':
            return scale(1, transformation[1], vec)
        elif transformation[0] == '-S':
            return scale(transformation[1], transformation[1], vec)
        elif transformation[0] == '-Cx':
            return shear(transformation[1], 0, vec)
        elif transformation[0] == '-Cy':
            return shear(0, transformation[1], vec)
        elif transformation[0] == '-C':
            return shear(transformation[1], transformation[1], vec)
        else:
            print_usage()

    def apply_transformations(vec):
        for t in transformations:
            vec = apply_transformation(t, vec)
        return vec

    ret = []
    for cmd in hpgl:
        if cmd[0] == 'PD' or cmd[0] == 'PU':
            acc = [cmd[0]]
            for vec in [cmd[i:i+2] for i in range(1, len(cmd), 2)]:
                acc.extend(apply_transformations(vec))
            ret.append(acc)
        else:
            ret.append(cmd)
    return ret


def parse_args():
    transforms = []
    files = []
    try:
        for i in range(1, len(sys.argv), 2):
            if sys.argv[i].startswith('-'):
                transforms.append([
                    sys.argv[i],
                    float(sys.argv[i + 1]),
                    ])
            else:
                files = sys.argv[i:]
                break
    except:
        print_usage()

    return (transforms, files)


def print_usage():
    print """Usage: {} [-Tx num] [-Ty num] [-R num] [-Sx num] [-Sy num] [-Cx num] [-Cy num] [files ...]

Apply transformations to all points in HPGL input. These transformations will
be applied in the given order, you can use the same transformation multiple
times. If no files are specified HPGL input is read from stdin and written to
stdout. Files are altered inplace so backup them.

Options:
    -T  num     translate by num pixels along both axies
    -Tx num     translate by num pixels along x axis
    -Ty num     translate by num pixels along y axis
    -R  num     rotate by num degrees ccw
    -S  num     scale by factor
    -Sx num     scale by factor num along x axis
    -Sy num     scale by factor num along y axis
    -C  num     shear by factor num
    -Cx num     shear by factor num along x axis
    -Cy num     shear by factor num along y axis
    """.format(sys.argv[0])
    sys.exit(1)


if __name__ == '__main__':
    transforms, files = parse_args()

    if not files:
        hpgl = decode_hpgl(sys.stdin.read())
        hpgl = do_transformations(transforms, hpgl)
        print encode_hpgl(hpgl),
    else:
        for f in files:
            with open(f, 'r') as fin:
                hpgl = decode_hpgl(fin.read())
            hpgl = do_transformations(transforms, hpgl)
            with open(f, 'w') as fout:
                fout.write(encode_hpgl(hpgl))
