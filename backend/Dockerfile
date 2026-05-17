# use python container image
FROM python:3.10

# Install OpenJDK-11
RUN apt-get update && \
    apt-get install -y openjdk-21-jdk && \
    apt-get clean;    

# set the working directory of the image filesystem 
WORKDIR /irex-backend

# copy current directory to the working directory 
ADD . /irex-backend

# Install the python dependencies
RUN pip install -r requirements.txt
