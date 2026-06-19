package com.example.puntodeventabackend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
public class ProductoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaProductoEntity categoria;

    @Column(name = "proveedor_id", columnDefinition = "BIGINT DEFAULT 1")
    private Long proveedorId;

    @Column(name = "codigo_barras")
    private String codigoBarras;

    @Column(name = "precio", nullable = false)
    private BigDecimal precio;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo;

    @Column(name = "unidad", nullable = false)
    private String unidad;

    @Column(name = "imagen_url")
    private String imagenUrl;

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public CategoriaProductoEntity getCategoria() {
        return categoria;
    }

    public Long getProveedorId() {
        return proveedorId;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public Integer getStock() {
        return stock;
    }

    public Integer getStockMinimo() {
        return stockMinimo;
    }

    public String getUnidad() {
        return unidad;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }
}
