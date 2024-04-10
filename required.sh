#!/bin/bash

sudo apt-get update
sudo apt-get install librsvg2-bin cmake

git clone --depth 1 -b master https://github.com/rism-digital/verovio /tmp/verovio 
cd /tmp/verovio/tools 
cmake ../cmake 
make -j 8 
make install 
sudo cp /tmp/verovio/fonts/Leipzig/Leipzig.ttf /usr/local/share/fonts/ 
fc-cache
