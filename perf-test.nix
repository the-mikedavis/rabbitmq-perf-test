{ lib, stdenv, buildMavenRepositoryFromLockFile, maven, graalvm11-ce, glibcLocales, nix-gitignore }:

let
  mavenRepository = buildMavenRepositoryFromLockFile { file = ./mvn2nix-lock.json; };
  version = "2.19.0-SNAPSHOT";
in stdenv.mkDerivation rec {
  pname = "perf-test";
  inherit version;
  name = "${pname}-${version}";
  src = nix-gitignore.gitignoreSource ["*.nix"] ./.;

  nativeBuildInputs = [ maven graalvm11-ce glibcLocales ];
  buildPhase = ''
    echo "Building with maven repository ${mavenRepository}"
    mvn package --offline \
      -Dmaven.repo.local=${mavenRepository} \
      -P native-image -P '!java-packaging' \
      -DskipTests

    echo "Constructing native binary with GraalVM"
    native-image -jar target/${pname}.jar -H:Features="com.rabbitmq.perf.NativeImageFeature" \
      --static \
      --initialize-at-build-time=io.micrometer \
      --initialize-at-build-time=com.rabbitmq.client \
      --initialize-at-build-time=org.slf4j \
      --no-fallback \
      -H:IncludeResources="rabbitmq-perf-test.properties"
  '';

  installPhase = ''
    install -Dm755 ${pname} -t $out/bin
  '';
}
