"""Lista os testes que falharam a partir dos relatórios JUnit (XML) do Gradle.

Uso: python failed_tests.py <pasta-raiz> — imprime uma linha por teste: `módulo · Classe.teste`.
Lê os resultados de unidade (`build/test-results`, incluindo simulador iOS e web app em Wasm/Node) e os
dos testes instrumentados e E2E do Android (`build/outputs/androidTest-results`).
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def failed_tests(root: Path) -> list[str]:
    found = []
    reports = {*root.glob("**/build/test-results/**/*.xml"), *root.glob("**/build/outputs/androidTest-results/**/*.xml")}
    for report in sorted(reports):
        relative = report.relative_to(root).parts
        module = "/".join(relative[:relative.index("build")]) or "?" if "build" in relative else "?"
        try:
            tree = ET.parse(report)
        except ET.ParseError:
            found.append(f"{module} · relatório ilegível: {report.name}")
            continue
        for case in tree.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                found.append(f"{module} · {case.get('classname', '?')}.{case.get('name', '?')}")
    return found


if __name__ == "__main__":
    print("\n".join(failed_tests(Path(sys.argv[1]))))
