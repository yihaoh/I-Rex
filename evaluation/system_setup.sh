#!/bin/bash

######################################################################
# Python 3.11:
sudo apt-get -qq --yes install python3.11
sudo update-alternatives --install /usr/bin/python python /usr/bin/python3.11 3
sudo apt-get -qq --yes install python3-pip
sudo apt-get -qq --yes install python-is-python3

######################################################################
# PostgreSQL client and development libraries:
sudo apt-get install postgresql
sudo apt-get -qq --yes install postgresql-client libpq-dev postgresql-server-dev-all
python -m pip install --break-system-packages psycopg2-binary SQLAlchemy
