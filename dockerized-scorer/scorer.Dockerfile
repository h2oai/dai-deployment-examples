FROM ubuntu:18.04

RUN apt-get update && apt-get install -y \
    python3.6\
    python3-pip\
    python3-virtualenv\
    default-jre\
    libopenblas-dev\
    python3.6-dev\
    python3-dev \
    python-virtualenv\
    git

RUN python3 -m pip install --upgrade pip

RUN mkdir -pv /wheels
WORKDIR /wheels

COPY requirements.txt http_server_requirements.txt ./

#split the requirements into downloaded packages, scoring wheel, and other wheels. The wheels are split to minimize the invalidating
#the docker cache when the scoring wheel (model) is updated.

RUN grep -v '.whl$' requirements.txt > non_wheel_requirements.txt && \
    python3 -m pip install -r non_wheel_requirements.txt -r http_server_requirements.txt

#do this in stages so as not to invalidate the docker cache on the frequently changing scorer.
#My example scorer is about 3mb, the image before that is about 3.6gb.

#this assumes there are no other wheels other than h2o* datatable* and scoring*, which is so far true
COPY h2o*.whl datatable*.whl ./
RUN python3 -m pip install --find-links .  ./*.whl

COPY scoring*.whl ./
RUN python3 -m pip install --find-links . scoring*.whl

RUN mkdir -pv /service/tmp
WORKDIR /service
#tmp contains custom recipes, transformers, etc.
COPY tmp  ./tmp
#the below is for the service itself
COPY *.py ./

#tell python that stdout/stderr uses utf8 and not ascii encoding
ENV PYTHONIOENCODING=utf-8
EXPOSE 9090
CMD ["python3", "http_server.py", "--port=9090"]
