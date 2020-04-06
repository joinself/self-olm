#!/bin/bash

# pre-package:
# make

fpm -s dir -t rpm -n libselfid-olm -v 0.0.1 -d libsodium-devel\
  ./build/libolm.so=/usr/lib/ \
  ./build/libolm.so.3=/usr/lib/ \
  ./build/libolm.so.3.1.4=/usr/lib/ \
  ./include/olm/=/usr/include/olm


# install:
# sudo yum install epel-release
# sudo yum repolist
# sudo yum install ./libselfid-olm-0.0.1-1.x86_64.rpm 
