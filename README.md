## KRPC (Version 5)

A minimal Java RPC framework demo.

### Modules
- krpc-api: public interfaces and models
- krpc-common: message schema, serializers, codec
- krpc-core: client/server, load-balancer, retry, breaker
- krpc-provider: service implementation (provider demo)
- krpc-consumer: client caller (consumer demo)

### How it works
- Provider registers and exposes services
- Consumer calls via dynamic proxy
- Netty transport with pluggable serialization
- Service discovery and load balancing

### Quick start
1. Run provider: `ProviderTest`
2. Run consumer: `ConsumerTest`

That's it.


