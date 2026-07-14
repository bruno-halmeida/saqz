if (JavaVersion.current() != JavaVersion.VERSION_21) {
    error("Saqz project gates require JDK 21; detected ${JavaVersion.current()}.")
}
