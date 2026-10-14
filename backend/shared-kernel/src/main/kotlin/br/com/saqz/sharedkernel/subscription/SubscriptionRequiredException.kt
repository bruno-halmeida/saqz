package br.com.saqz.sharedkernel.subscription

class SubscriptionRequiredException : RuntimeException()

/** Request attribute set when the group owner's plan is paused but the endpoint decides per payload what is allowed. */
const val PLAN_READ_ONLY_REQUEST_ATTRIBUTE = "saqz.plan.readOnly"
