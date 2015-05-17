# Set up datomic-free as a docker

# This is based pretty heavily on tauho/docker-datomic-free
# That includes the copyright and license for Datomic Free
# (which means I need to also).

# From Timo Sulg, aka tauho:
# Any Dockerfile and scripts in that repository are licensed
# as WTFpl (https://tldrlegal.com/license/do-wtf-you-want-to-public-license-v2-(wtfpl-2.0)
# - you can use and modify everything in the repo - Cognitect licences applies [sic] only
# to Datomic's images.

FROM dockerfile/java

RUN apt-get update && apt-get -y \
    curl \
    unzip

RUN groupadd -r datomic && useradd -r -g datomic datomic
USER datomic

RUN mkdir ~/datomic

ENV DATOMIC_VERSION 0.9.5173

RUN curl --user-agent 'frereth (builds@frereth.com' \
    --location \
    --url "https://my.datomic.com/downloads/free/${DATOMIC_VERSION}" \
    --output ~/datomic.zip

RUN unzip -u ~/datomic.zip -d ~
RUN mv ~/datomic-free-${DATOMIC_VERSION} ~/datomic/
RUN rm ~/datomic.zip

RUN cp ~/datomic/config/samples/free-transactor-template.properties ~/datomic/free-transactor.properties

RUN sed "s/host=localhost/host=0.0.0.0" -i ~/datomic/free-transactor.properties

ENTRYPONT ["/home/datomic/datomic/bin/transactor"]
CMD ["/home/datomic/datomic/free-transactor.properties" ]

EXPOSE 4334 4335 4336
