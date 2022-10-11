#!/usr/bin/env bash

# The creation of this flake took quite a while since it seems like
# the Maven build tool is not the best-supported build tool in the
# Nix community. There are a few tools but none of them seem very
# actively maintained and all of the lack support for Maven profiles.
#
# This maven project is built simply with
#
#    mvn clean package -P uber-jar -Dgpg.skip=true -Dmaven.test.skip=true
#
# Passing `-D` is not a problem with most community tools. The `-P`
# profile flag _is_ though. This is a Spring Boot project which needs
# some extra plugins in the `uber-jar` profile. All existing tools
# fail to download these other plugins.
#
# I've included a script which turns a maven repository into a JSON
# file which can be used by the github:fzakaria/mvn2nix tool. This
# is based on this comment:
# https://github.com/NixOS/nixpkgs/issues/19741#issuecomment-346225816
#
# To use it, build the project in the `uber-jar` profile using a local
# maven repository in some directory. Let's call it `foo/`. Drop into
#
#     nix shell nixpkgs#maven
#     mkdir foo/
#     mvn clean package -P uber-jar -Dgpg.skip=true -Dmaven.test.skip=true -Dmaven.repo.local=foo
#
# Now the `foo/` directory contains the maven repository. Then run
# the script to generate the `mvn2nix-lock.json` file:
#
#     ./mvn-repo-to-json.sh foo/ > mvn2nix-lock.json
#
# I'm lazy and didn't figure out a nice way to remove the trailing comma :P.
# Open up a text editor and remove that.
#
# With all of this in place, we can now `nix build` to create
# `result/bin/perf-test`. Use this distribution and script as you see fit.
# I'll drop a 0BSD license for you here :)
#
# Copyright (C) 2022 Michael Davis
# Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted.
# THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.


echo "{"
echo "  \"dependencies\": {"

for f in $(find $1 -type f -not -name \*.sha1 -not -name _remote.repositories -not -name \*.lastUpdated | sort); do
    groupId=$(echo $(dirname $(dirname $(dirname $f))) | sed "s|^$1||" | sed "s|/|.|g");
    filename=$(basename $f);
    version=$(basename $(dirname $f));
    artifactId=$(basename $(dirname $(dirname $f)));
    type=$(echo $filename | rev | cut -d . -f 1 | rev);
    sha256=$(sha256sum $f | cut -d " " -f 1);
    layout=$(echo $f | sed "s|^$1||")

    cat <<EOF
    "$groupId:$artifactId:$type:$version": {
      "layout": "$layout",
      "sha256": "$sha256",
      "url": "https://repo.maven.apache.org/maven2/$layout"
    },
EOF
    # NOTE the trailing comma. You have to delete that yourself :/
done

echo "  }"
echo "}"
