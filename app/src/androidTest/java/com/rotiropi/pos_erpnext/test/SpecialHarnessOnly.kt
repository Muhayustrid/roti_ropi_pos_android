package com.rotiropi.pos_erpnext.test

/**
 * Marker for setup/verification methods driven only by repo host scripts
 * (tools/oauth-process-death.sh, tools/run-device-tests.sh).
 *
 * Broad device runs exclude this annotation; the OAuth host script invokes the
 * annotated class/method names explicitly.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class SpecialHarnessOnly
