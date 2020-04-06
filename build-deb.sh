#!/bin/bash

# build:
# make

fpm -s dir -t deb -n libselfid-olm -v 0.0.1 --deb-no-default-config-files -d libsodium-dev\
  ./build/libolm.so=/usr/lib/ \
  ./build/libolm.so.3=/usr/lib/ \
  ./build/libolm.so.3.1.4=/usr/lib/ \
  ./include/olm/=/usr/include/olm

# install:
# sudo apt update
# sudo apt install ./libselfid-olm_0.0.1_amd64.deb
