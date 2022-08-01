{
  description = "A load testing tool for RabbitMQ";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    mvn2nix.url = "github:fzakaria/mvn2nix";
    utils.url = "github:numtide/flake-utils";
  };

  outputs = { nixpkgs, mvn2nix, utils, ... }:
    let
      overlay = (final: prev: {
        perf-test = final.callPackage ./perf-test.nix { };
      });
      pkgs' = system: import nixpkgs {
        inherit system;
        overlays = [ mvn2nix.overlay overlay ];
      };
    in utils.lib.eachSystem utils.lib.defaultSystems (system: rec {
      legacyPackages = pkgs' system;
      packages = utils.lib.flattenTree { inherit (legacyPackages) perf-test; };
      defaultPackage = legacyPackages.perf-test;
    });
}
