#!/bin/bash
protoc -I=D:\\JAVA-projects\\Raft\\Raft-01\\src\\main\\resources  --descriptor_set_out=raft.desc --java_out=../java/ enum.proto local_file_meta.proto raft.proto local_storage.proto rpc.proto cli.proto log.proto
