#!/bin/sh
# Clones Winlator (and its app submodule) next to the project.
set -e
cd "$(dirname "$0")/.."
[ -d upstream-winlator/.git ] || git clone --depth 1 https://github.com/brunodev85/winlator.git upstream-winlator
git -C upstream-winlator submodule update --init --depth 1 app
