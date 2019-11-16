FROM centos:centos7

# These commands run as root
# Install base dependencies
RUN yum -y update && \
    yum install -y epel-release && \
    yum -y groupinstall 'Development Tools' && \
    yum install -y openblas-devel openblas python-virtualenv python36-virtualenv python36-pip java supervisor

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

COPY server_setup/* /servers/
WORKDIR /servers
RUN /servers/setup_http_server.sh

COPY supervisord.conf /etc/supervisord.conf
RUN mkdir -p /var/log/supervisor

ENV DRIVERLESS_AI_LICENSE_FILE=/opt/ml/model/license.sig

ENTRYPOINT ["/usr/bin/supervisord", "-i"]

