package com.expensesplit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Habilita las tareas periodicas de TareasDeMantenimiento. Sin esta
// anotacion, los @Scheduled se ignoran en silencio: la aplicacion arranca
// igual, no se queja de nada, y la purga simplemente no ocurre nunca.
@EnableScheduling
@SpringBootApplication
public class ExpenseSplitApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseSplitApplication.class, args);
    }

}
