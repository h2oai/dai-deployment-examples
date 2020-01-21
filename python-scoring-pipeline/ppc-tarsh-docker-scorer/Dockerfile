FROM centos:centos7

RUN yum -y update && \
    yum install -y epel-release && \
    yum -y groupinstall 'Development Tools' && \
    yum install -y openblas-devel openblas wget unzip java

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

COPY payload/dai-*.sh ./
RUN chmod 755 dai-*.sh && \
    ./dai-*.sh && \
    rm dai-*.sh && \
    chmod -R a+w dai-*/python

ENV SCORING_PIPELINE_INSTALL_DEPENDENCIES=0

COPY payload/license.sig /root/.driverlessai/
COPY payload/scorer.zip ./ 
RUN unzip scorer.zip && \
    dai-*/dai-env.sh pip install scoring-pipeline/scoring*whl

CMD dai-*/dai-env.sh python scoring-pipeline/example.py

