{ lib, stdenv, buildMavenRepositoryFromLockFile, makeWrapper, maven, jdk11_headless, nix-gitignore }:

let
  mavenRepository = buildMavenRepositoryFromLockFile { file = ./mvn2nix-lock.json; };
in stdenv.mkDerivation rec {
  pname = "perf-test";
  version = "2.19.0-SNAPSHOT";
  name = "${pname}-${version}";
  src = nix-gitignore.gitignoreSource ["*.nix"] ./.;

  nativeBuildInputs = [ jdk11_headless maven makeWrapper ];
  buildPhase = ''
    echo "Building with maven repository ${mavenRepository}"
    mvn package --offline -P uber-jar \
      -Dmaven.repo.local=${mavenRepository} \
      -Dgpg.skip=true -Dmaven.test.skip
  '';

  installPhase = ''
    mkdir -p $out/bin
    ln -s ${mavenRepository} $out/lib
    cp target/${pname}.jar $out/
    makeWrapper ${jdk11_headless}/bin/java $out/bin/${pname} \
      --add-flags "-jar $out/${pname}.jar"
  '';
}
