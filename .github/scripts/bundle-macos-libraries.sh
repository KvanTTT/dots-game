#!/usr/bin/env bash
# Makes the engine run on a Mac that has no Homebrew on it.
#
# The Metal backend of KataGoDots is linked against protobuf and abseil, which macOS itself doesn't
# carry, so every library of the engine that isn't a part of the system is copied next to it and the
# path it is loaded by is rewritten to `@executable_path`, the libraries of the libraries included.
#
# Usage: bundle-macos-libraries.sh <binary>
set -euo pipefail

binary=$1
libraries="$(dirname "$binary")/libs"
mkdir -p "$libraries"

# A library of the system is loaded from the machine itself; everything else is brought along
is_of_the_system() {
    [[ $1 == /usr/lib/* || $1 == /System/* || $1 == @* ]]
}

# The signature of a binary doesn't survive its load commands being rewritten, and macOS runs nothing
# on Apple silicon that isn't signed at all
resign() {
    codesign --force --sign - "$1" 2>/dev/null || true
}

# A library that is looked up by `@rpath` is found next to the one that loads it, and every path that
# led into Homebrew is dropped, so that nothing of the machine that built the engine is looked at
point_rpath_at_the_bundle() {
    local file=$1 own_rpath=$2
    while read -r rpath; do
        [[ $rpath == /opt/homebrew/* || $rpath == /usr/local/* ]] && install_name_tool -delete_rpath "$rpath" "$file"
    done < <(otool -l "$file" | awk '/LC_RPATH/{ getline; getline; print $2 }')
    install_name_tool -add_rpath "$own_rpath" "$file" 2>/dev/null || true
}

queue=("$binary")
point_rpath_at_the_bundle "$binary" "@executable_path/libs"
while ((${#queue[@]} > 0)); do
    current=${queue[0]}
    queue=("${queue[@]:1}")

    while read -r dependency; do
        is_of_the_system "$dependency" && continue

        name=$(basename "$dependency")
        if [[ ! -f $libraries/$name ]]; then
            cp "$dependency" "$libraries/$name"
            chmod u+w "$libraries/$name"
            install_name_tool -id "@executable_path/libs/$name" "$libraries/$name"
            point_rpath_at_the_bundle "$libraries/$name" "@loader_path"
            queue+=("$libraries/$name")
        fi
        install_name_tool -change "$dependency" "@executable_path/libs/$name" "$current"
    done < <(otool -L "$current" | tail -n +2 | awk '{print $1}')

    resign "$current"
done

# Nothing may be left to look for outside the bundle and the system itself
for file in "$binary" "$libraries"/*; do
    while read -r dependency; do
        is_of_the_system "$dependency" && continue
        if [[ $dependency == @rpath/* ]]; then
            [[ -f $libraries/$(basename "$dependency") ]] ||
                { echo "$(basename "$file") looks for $dependency, which is nowhere in the bundle" >&2; exit 1; }
        elif [[ $dependency != @executable_path/* ]]; then
            echo "$(basename "$file") is still linked against $dependency" >&2
            exit 1
        fi
    done < <(otool -L "$file" | tail -n +2 | awk '{print $1}')
done

echo "The engine is shipped with $(ls "$libraries" | wc -l | tr -d ' ') libraries of its own"
