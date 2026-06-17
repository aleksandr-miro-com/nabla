package com.miro.demo

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Import

@Import(SimpleMeterRegistry::class)
@SpringBootApplication
class DemoApplication