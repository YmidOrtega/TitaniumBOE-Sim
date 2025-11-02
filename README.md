TitaniumBOE-Sim

💹 A Java-based simulator for the Cboe Titanium U.S. Options Binary Order Entry protocol

📌 Descripción

TitaniumBOE-Sim es un simulador en Java puro del protocolo Cboe Titanium U.S. Options Binary Order Entry (BOE), diseñado para replicar el flujo de comunicación cliente-servidor definido por Cboe.Este proyecto es ideal para aprender y experimentar con protocolos binarios de mercados financieros sin necesidad de conectarse a un entorno de producción real.

🚀 Características

📡 Comunicación TCP/IP simulada cliente-servidor.

📦 Serialización y deserialización de mensajes binarios (Little Endian).

🔑 Gestión de sesión: Login, Heartbeat, Logout.

📈 Flujo de órdenes: creación, modificación, cancelación y rechazo.

🛡 Simulación de validaciones (Fat Finger, NBBO, límites).

🔍 Logging en formato hexadecimal y decodificado.

🖥 Arquitectura

sequenceDiagram
    participant Client as BOE Client (Java)
    participant Server as BOE Server Mock
    Client->>Server: Login Request
    Server-->>Client: Login Response
    loop Heartbeats
        Client->>Server: Heartbeat
        Server-->>Client: Heartbeat
    end
    Client->>Server: New Order
    Server-->>Client: Order Acknowledgement
    Client->>Server: Cancel Order
    Server-->>Client: Order Cancelled
    Client->>Server: Logout Request
    Server-->>Client: Logout Response


⚙️ Requisitos

Java 17 o superior.

Maven 3.8+ (para compilar y gestionar dependencias).

📦 Instalación

git clone https://github.com/TU_USUARIO/TitaniumBOE-Sim.git
cd TitaniumBOE-Sim
mvn compile

▶️ Ejecución

mvn exec:java -Dexec.mainClass="com.boe.simulator.ClientServer"

📜 Licencia

Este proyecto es solo para fines educativos y de simulación.No se conecta ni envía órdenes reales a Cboe.Licencia: MIT.

