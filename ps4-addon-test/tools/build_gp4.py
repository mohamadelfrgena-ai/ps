#!/usr/bin/env python3
"""Write a Sony .gp4 project file that LibOrbisPkg (PkgTool.Core) accepts.

We generate the gp4 ourselves because the toolchain's create-gp4 builds an
unreliable <rootdir> tree for nested asset folders, which makes PkgTool crash
in LibOrbisPkg.PFS.PfsProperties.FindDir ("Sequence contains no elements").
Here the directory tree is derived directly from the file list, so every
parent directory of every file is guaranteed to exist in <rootdir>.

Usage:
    python3 tools/build_gp4.py <out.gp4> <CONTENT_ID> <file> [<file> ...]

File paths are package-relative POSIX paths (e.g. sce_sys/param.sfo).
"""
import datetime
import sys


def dir_tree_xml(node, indent):
    lines = []
    for name in sorted(node.keys()):
        child = node[name]
        if child:
            lines.append('%s<dir targ_name="%s">' % (indent, name))
            lines.extend(dir_tree_xml(child, indent + "\t"))
            lines.append('%s</dir>' % indent)
        else:
            lines.append('%s<dir targ_name="%s" />' % (indent, name))
    return lines


def build(out_path, content_id, files):
    root = {}
    for f in files:
        parts = f.split("/")
        node = root
        for part in parts[:-1]:
            node = node.setdefault(part, {})

    file_tags = [
        '\t\t<file targ_path="%s" orig_path="%s" />' % (f, f) for f in files
    ]
    rootdir_lines = dir_tree_xml(root, "\t\t")
    ts = datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S")

    doc = [
        '<?xml version="1.0"?>',
        '<psproject xmlns:xsd="http://www.w3.org/2001/XMLSchema"'
        ' xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"'
        ' fmt="gp4" version="1000">',
        '\t<volume>',
        '\t\t<volume_type>pkg_ps4_app</volume_type>',
        '\t\t<volume_id>PS4VOLUME</volume_id>',
        '\t\t<volume_ts>%s</volume_ts>' % ts,
        '\t\t<package content_id="%s" passcode="00000000000000000000000000000000"'
        % content_id,
        '\t\t\tstorage_type="digital50" app_type="full" />',
        '\t\t<chunk_info chunk_count="1" scenario_count="1">',
        '\t\t\t<chunks>',
        '\t\t\t\t<chunk id="0" layer_no="0" label="Chunk #0" />',
        '\t\t\t</chunks>',
        '\t\t\t<scenarios default_id="0">',
        '\t\t\t\t<scenario id="0" type="sp" initial_chunk_count="1"'
        ' label="Scenario #0">0</scenario>',
        '\t\t\t</scenarios>',
        '\t\t</chunk_info>',
        '\t</volume>',
        '\t<files img_no="0">',
    ]
    doc.extend(file_tags)
    doc.append('\t</files>')
    doc.append('\t<rootdir>')
    doc.extend(rootdir_lines)
    doc.append('\t</rootdir>')
    doc.append('</psproject>')
    doc.append('')

    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(doc))
    print("wrote %s with %d files" % (out_path, len(files)))


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 2
    build(sys.argv[1], sys.argv[2], sys.argv[3:])
    return 0


if __name__ == "__main__":
    sys.exit(main())
