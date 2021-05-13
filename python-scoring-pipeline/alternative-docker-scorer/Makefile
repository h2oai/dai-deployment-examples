#
# Docker image tagging
#
DOCKER_VERSION_TAG ?= "latest"

#
# Build Ubunut 18.04 Docker Image for Python Scoring Pipeline
#
python-scoring-pipeline-ubuntu18.04:
	@echo "+--Building Runtime Docker Image--+"
	docker build \
		-t dai-python-scoring-pipeline:$(DOCKER_VERSION_TAG) \
		-f Dockerfile.in .
