if (JavaVersion.current() != JavaVersion.VERSION_21) {
    error("Saqz mobile project gates require JDK 21; detected ${JavaVersion.current()}.")
}
