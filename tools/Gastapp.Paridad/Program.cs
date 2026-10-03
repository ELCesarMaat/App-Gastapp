using System.Globalization;
using System.Text.Json;
using Gastapp.Models;
using Gastapp.Paridad.Referencia;

// Genera los fixtures de paridad. Todo es determinista (semillas fijas): correrlo dos
// veces da exactamente los mismos archivos.

var salida = args.Length > 0
    ? args[0]
    : Path.Combine("Gastapp.Android", "domain", "src", "test", "resources", "paridad");
Directory.CreateDirectory(salida);

var inv = CultureInfo.InvariantCulture;
string F(DateTime d) => d.ToString("yyyy-MM-dd", inv);
string FH(DateTime d) => d.ToString("yyyy-MM-ddTHH:mm:ss", inv);
string D(decimal v) => v.ToString(inv);
string R(double v) => v.ToString("R", inv);

IEnumerable<DateTime> Dias(DateTime desde, DateTime hasta, int paso = 1)
{
    for (var d = desde; d <= hasta; d = d.AddDays(paso))
        yield return d;
}

decimal Monto(Random azar) => Math.Round(azar.Next(100, 500_000) / 100m, 2);

void Guardar(string nombre, object contenido)
{
    var ruta = Path.Combine(salida, nombre);
    File.WriteAllText(ruta, JsonSerializer.Serialize(contenido));
    Console.WriteLine($"{nombre,-32} {new FileInfo(ruta).Length / 1024,6} KB");
}

// ---------------------------------------------------------------------------
// 1. Fechas de corte y pago (calendario puro)
// ---------------------------------------------------------------------------
{
    var dias = new[] { 1, 4, 15, 25, 28, 30, 31 };
    var referencias = Dias(new DateTime(2026, 1, 1), new DateTime(2026, 12, 31))
        .Concat(Dias(new DateTime(2028, 2, 1), new DateTime(2028, 3, 2)))
        .ToList();

    var casos = new List<object[]>();
    foreach (var corte in dias)
    foreach (var pago in dias)
    foreach (var hoy in referencias)
    {
        var (c, p) = TarjetasReferencia.CalculateCycleDates(corte, pago, hoy);
        var ultimoCorte = TarjetasReferencia.GetLastCutOffDate(corte, hoy);
        casos.Add([corte, pago, F(hoy), F(c), F(p), F(ultimoCorte)]);
    }

    Guardar("ciclos_tarjeta.json", new { casos });
}

// ---------------------------------------------------------------------------
// 2. Escenarios completos de tarjeta: deuda, fecha limite ajustada, ciclo, MSI...
// ---------------------------------------------------------------------------
{
    var azar = new Random(20261002);
    var plazos = new[] { 3, 6, 9, 12, 18, 24 };
    var escenarios = new List<object>();

    for (var n = 0; n < 1500; n++)
    {
        var hoy = new DateTime(2025, 1, 1).AddDays(azar.Next(0, 1095));
        var card = new CreditCard
        {
            CreditCardId = "T",
            UserId = "U",
            CardName = "Tarjeta",
            BankName = "Banco",
            CutOffDay = azar.Next(1, 32),
            PaymentDay = azar.Next(1, 32),
            CreditLimit = azar.Next(0, 5) == 0 ? 0m : azar.Next(5, 101) * 1000m,
        };

        Spending Mov(bool esCompra, decimal monto, DateTime fecha) => new()
        {
            SpendingId = Guid.NewGuid().ToString(),
            UserId = "U",
            CategoryId = "C",
            Title = "x",
            CreditCardId = "T",
            IsCreditCard = esCompra,
            Amount = monto,
            Date = fecha,
        };

        var movs = new List<Spending>();
        var compras = azar.Next(0, 8);
        for (var i = 0; i < compras; i++)
        {
            var monto = Monto(azar);
            var mov = Mov(true, monto, hoy.AddDays(azar.Next(-75, 6)).AddMinutes(azar.Next(0, 1440)));
            if (azar.Next(0, 5) == 0)
            {
                mov.IsMsi = true;
                mov.TotalInstallments = plazos[azar.Next(plazos.Length)];
                mov.CurrentInstallment = azar.Next(1, mov.TotalInstallments + 1);
                mov.InstallmentMonthlyAmount = azar.Next(0, 4) == 0 ? 0m : Math.Round(monto / mov.TotalInstallments, 2);
            }
            if (azar.Next(0, 10) == 0) mov.CreditCardId = "OTRA";
            if (azar.Next(0, 12) == 0) mov.IsDeleted = true;
            movs.Add(mov);
        }

        var pagos = azar.Next(0, 4);
        for (var i = 0; i < pagos; i++)
        {
            var mov = Mov(false, Monto(azar), hoy.AddDays(azar.Next(-60, 1)).AddMinutes(azar.Next(0, 1440)));
            if (azar.Next(0, 15) == 0) mov.IsDeleted = true;
            movs.Add(mov);
        }

        // Para cubrir la rama del "corte ya pagado", a veces se abona justo lo facturado
        // (o medio centavo de mas o de menos, para probar la tolerancia).
        if (azar.Next(0, 3) == 0)
        {
            var (_, pagoSinAjuste) = TarjetasReferencia.CalculateCycleDates(card.CutOffDay, card.PaymentDay, hoy);
            var corteEdoCta = TarjetasReferencia.PreviousOccurrenceOfDay(pagoSinAjuste.AddDays(-1), card.CutOffDay);
            var facturado = movs.Where(s => s.CreditCardId == "T" && s.IsCreditCard && !s.IsDeleted && s.Date < corteEdoCta.AddDays(1)).Sum(s => s.Amount);
            var pagado = movs.Where(s => s.CreditCardId == "T" && !s.IsCreditCard && !s.IsDeleted).Sum(s => s.Amount);
            var falta = facturado - pagado + (azar.Next(0, 3) - 1) * 0.005m;
            if (falta > 0)
                movs.Add(Mov(false, falta, hoy.AddDays(-azar.Next(0, 5)).AddMinutes(azar.Next(0, 1440))));
        }

        var resumen = TarjetasReferencia.GetCardSummary(card, movs, hoy);
        var nuevoSaldo = azar.Next(0, 4) == 0 ? resumen.TotalDebt : Monto(azar) * azar.Next(0, 3);
        var ajuste = TarjetasReferencia.AdjustCardBalance(resumen.TotalDebt, nuevoSaldo);

        escenarios.Add(new
        {
            corte = card.CutOffDay,
            pago = card.PaymentDay,
            limite = D(card.CreditLimit),
            hoy = F(hoy),
            // [esDeLaTarjeta, esCompra, borrado, monto, fecha, esMsi, plazo, mensualidadActual, mensualidad]
            movs = movs.Select(s => new object[]
            {
                s.CreditCardId == "T" ? 1 : 0, s.IsCreditCard ? 1 : 0, s.IsDeleted ? 1 : 0, D(s.Amount), FH(s.Date),
                s.IsMsi ? 1 : 0, s.TotalInstallments, s.CurrentInstallment, D(s.InstallmentMonthlyAmount),
            }).ToList(),
            nuevoSaldo = D(nuevoSaldo),
            esperado = new
            {
                corte = F(resumen.NextCutOffDate),
                pago = F(resumen.NextPaymentDueDate),
                deuda = D(resumen.TotalDebt),
                disponible = D(resumen.AvailableCredit),
                uso = R(resumen.UsagePercentage),
                cicloActual = D(resumen.CurrentCycleAmount),
                msiFuturo = D(resumen.TotalMsiRemainingDebt),
                msiActivas = resumen.ActiveMsiCount,
                diasCorte = resumen.DaysUntilCutOff,
                diasPago = resumen.DaysUntilPayment,
                colorPago = resumen.PaymentStatusColor,
                colorUso = resumen.UsageStatusColor,
                colorAhorros = TarjetasReferencia.SavesPendingColor(resumen.DaysUntilPayment),
                pagoSugerido = D(TarjetasReferencia.SuggestedPayment(resumen)),
                ajuste = ajuste is null
                    ? null
                    : new object[] { ajuste.Value.IsCreditCard ? 1 : 0, D(ajuste.Value.Amount), ajuste.Value.PaymentMethod },
            },
        });
    }

    Guardar("escenarios_tarjeta.json", new { escenarios });
}

// ---------------------------------------------------------------------------
// 3. Alta de una tarjeta que ya venia en uso
// ---------------------------------------------------------------------------
{
    var azar = new Random(31);
    var plazos = new[] { 3, 6, 9, 12, 18, 24 };
    var casos = new List<object>();

    for (var n = 0; n < 600; n++)
    {
        var ahora = new DateTime(2025, 1, 1).AddDays(azar.Next(0, 730)).AddMinutes(azar.Next(0, 1440));
        var corte = azar.Next(1, 32);
        var totalUsado = azar.Next(0, 6) == 0 ? 0m : Monto(azar) * azar.Next(1, 4);
        var cicloActual = azar.Next(0, 3) == 0 ? 0m : Monto(azar);
        var saldoYaCortado = azar.Next(0, 2) == 0;

        var msi = new List<(string, decimal, int, int)>();
        var cuantas = azar.Next(0, 4);
        for (var i = 0; i < cuantas; i++)
        {
            var plazo = plazos[azar.Next(plazos.Length)];
            msi.Add((azar.Next(0, 3) == 0 ? "" : $"Compra {i}", Math.Round(azar.Next(5_000, 300_000) / 100m, 2), azar.Next(0, plazo), plazo));
        }

        var movimientos = TarjetasReferencia.InUseCardMovements("Mi tarjeta", corte, totalUsado, cicloActual, saldoYaCortado, msi, ahora);

        casos.Add(new
        {
            corte,
            ahora = FH(ahora),
            totalUsado = D(totalUsado),
            cicloActual = D(cicloActual),
            saldoYaCortado,
            msi = msi.Select(m => new object[] { m.Item1, D(m.Item2), m.Item3, m.Item4 }).ToList(),
            // [titulo, monto, fecha, esMsi, mensualidad, mensualidadActual, plazo]
            esperado = movimientos.Select(m => new object[] { m.Title, D(m.Amount), FH(m.Date), m.IsMsi ? 1 : 0, D(m.Monthly), m.Current, m.Total }).ToList(),
        });
    }

    Guardar("tarjeta_en_uso.json", new { casos });
}

// ---------------------------------------------------------------------------
// 4. Cobros de suscripciones (proximo y anterior)
// ---------------------------------------------------------------------------
var ciclos = new[]
{
    SubscriptionBillingCycles.Weekly,
    SubscriptionBillingCycles.Monthly,
    SubscriptionBillingCycles.Quarterly,
    SubscriptionBillingCycles.Semiannual,
    SubscriptionBillingCycles.Yearly,
    "Desconocido",
};
{
    var anclas = new[]
    {
        new DateTime(2024, 2, 29), new DateTime(2025, 1, 31), new DateTime(2025, 2, 28), new DateTime(2025, 3, 30),
        new DateTime(2025, 5, 31), new DateTime(2025, 8, 15), new DateTime(2025, 12, 31), new DateTime(2026, 1, 1),
        new DateTime(2026, 6, 30), new DateTime(2026, 10, 2),
    };
    var referencias = Dias(new DateTime(2023, 12, 1), new DateTime(2028, 3, 1), 5).ToList();

    var casos = new List<object[]>();
    for (var a = 0; a < anclas.Length; a++)
    for (var c = 0; c < ciclos.Length; c++)
    foreach (var hoy in referencias)
    {
        var proximo = SuscripcionesReferencia.CalculateNextChargeDate(anclas[a], ciclos[c], hoy);
        var anterior = SuscripcionesReferencia.CalculatePreviousChargeDate(anclas[a], ciclos[c], hoy);
        casos.Add([a, c, F(hoy), F(proximo), F(anterior)]);
    }

    Guardar("cobros_suscripcion.json", new { anclas = anclas.Select(F).ToList(), ciclos, casos });
}

// ---------------------------------------------------------------------------
// 5. Resumen de una suscripcion
// ---------------------------------------------------------------------------
var metodos = new[]
{
    SubscriptionPaymentMethods.Cash, SubscriptionPaymentMethods.Debit,
    SubscriptionPaymentMethods.Transfer, SubscriptionPaymentMethods.CreditCard,
};

Subscription NuevaSuscripcion(Random azar, DateTime hoy, string nombre)
{
    var sub = new Subscription
    {
        SubscriptionId = Guid.NewGuid().ToString(),
        UserId = "U",
        ServiceName = nombre,
        Amount = Monto(azar),
        BillingCycle = ciclos[azar.Next(ciclos.Length)],
        FirstChargeDate = hoy.AddDays(azar.Next(-900, 60)),
        PaymentMethod = metodos[azar.Next(metodos.Length)],
        IsActive = azar.Next(0, 7) != 0,
        IsTrial = azar.Next(0, 4) == 0,
    };
    // Tambien hay fechas de fin de prueba sin prueba activa: el codigo las ignora.
    if (sub.IsTrial)
        sub.TrialEndDate = azar.Next(0, 10) == 0 ? null : hoy.AddDays(azar.Next(-20, 45));
    else if (azar.Next(0, 10) == 0)
        sub.TrialEndDate = hoy.AddDays(azar.Next(-20, 45));
    return sub;
}

{
    var azar = new Random(45);
    var escenarios = new List<object>();

    for (var n = 0; n < 1500; n++)
    {
        var hoy = new DateTime(2024, 6, 1).AddDays(azar.Next(0, 1300));
        var sub = NuevaSuscripcion(azar, hoy, "Servicio");
        sub.LastChargeRegisteredAt = azar.Next(0, 2) == 0
            ? null
            : hoy.AddDays(azar.Next(-120, 3)).AddMinutes(azar.Next(0, 1440));
        var totalMensual = azar.Next(0, 5) == 0 ? 0m : Monto(azar) * azar.Next(1, 4);

        var r = SuscripcionesReferencia.BuildSummary(sub, totalMensual, hoy);

        escenarios.Add(new
        {
            hoy = F(hoy),
            monto = D(sub.Amount),
            ciclo = sub.BillingCycle,
            primerCobro = F(sub.FirstChargeDate),
            activa = sub.IsActive,
            prueba = sub.IsTrial,
            finPrueba = sub.TrialEndDate is null ? null : F(sub.TrialEndDate.Value),
            ultimoCobro = sub.LastChargeRegisteredAt is null ? null : FH(sub.LastChargeRegisteredAt.Value),
            totalMensual = D(totalMensual),
            esperado = new
            {
                mensual = D(r.MonthlyEquivalent),
                anual = D(r.YearlyEquivalent),
                proximo = F(r.NextChargeDate),
                dias = r.DaysUntilCharge,
                textoCobro = r.ChargeStatusText,
                colorCobro = r.ChargeStatusColor,
                pruebaActiva = r.IsTrialActive,
                diasPrueba = r.DaysUntilTrialEnds,
                cuenta = r.CountsTowardTotals,
                parte = R(r.ShareOfMonthlyRatio),
                textoParte = r.ShareOfMonthlyText,
                cobrado = r.IsCurrentCycleCharged,
                insignia = r.StatusBadgeText,
                colorInsignia = r.StatusBadgeColor,
                siguientes = r.NextCharges.Select(F).ToList(),
            },
        });
    }

    Guardar("escenarios_suscripcion.json", new { escenarios });
}

// ---------------------------------------------------------------------------
// 6. Proximos cobros (ventana de 45 dias, todas las suscripciones)
// ---------------------------------------------------------------------------
{
    var azar = new Random(4545);
    var escenarios = new List<object>();

    for (var n = 0; n < 400; n++)
    {
        var hoy = new DateTime(2024, 6, 1).AddDays(azar.Next(0, 1300));
        var subs = new List<Subscription>();
        var cuantas = azar.Next(1, 7);
        for (var i = 0; i < cuantas; i++)
        {
            // Nombres en mayusculas ASCII: asi el orden es igual en cualquier cultura.
            var sub = NuevaSuscripcion(azar, hoy, $"S{(char)('A' + azar.Next(26))}{i}");
            if (azar.Next(0, 12) == 0) sub.IsDeleted = true;
            subs.Add(sub);
        }

        var cobros = SuscripcionesReferencia.GetUpcomingCharges(subs, hoy);

        escenarios.Add(new
        {
            hoy = F(hoy),
            // [nombre, ciclo, primerCobro, activa, prueba, finPrueba, borrada]
            subs = subs.Select(s => new object?[]
            {
                s.ServiceName, s.BillingCycle, F(s.FirstChargeDate), s.IsActive, s.IsTrial,
                s.TrialEndDate is null ? null : F(s.TrialEndDate.Value), s.IsDeleted,
            }).ToList(),
            // [indice, fecha, diasFaltan, texto, color]
            esperado = cobros.Select(c => new object[] { c.Indice, F(c.Date), c.DaysUntil, c.WhenText, c.StatusColor }).ToList(),
        });
    }

    Guardar("proximos_cobros.json", new { escenarios });
}

// ---------------------------------------------------------------------------
// 7. Periodos de pago
// ---------------------------------------------------------------------------
{
    var configuraciones = new List<(int Tipo, int? Primero, int? Segundo)>();
    for (var d = 0; d <= 6; d++) configuraciones.Add((1, d, null));
    configuraciones.Add((1, null, null));
    foreach (var (a, b) in new (int?, int?)[] { (1, 15), (15, 30), (30, 15), (31, 15), (5, 20), (15, 15), (null, null), (1, null) })
        configuraciones.Add((2, a, b));
    foreach (var d in new int?[] { 1, 5, 15, 28, 29, 30, 31, null })
        configuraciones.Add((3, d, null));
    configuraciones.Add((0, 10, null)); // tipo desconocido: se trata como mensual

    var referencias = Dias(new DateTime(2026, 1, 1), new DateTime(2026, 12, 31), 3)
        .Concat(Dias(new DateTime(2028, 2, 20), new DateTime(2028, 3, 5)))
        .ToList();

    var casos = new List<object?[]>();
    foreach (var (tipo, primero, segundo) in configuraciones)
    foreach (var hoy in referencias)
    for (var desplazamiento = 0; desplazamiento <= 4; desplazamiento++)
    {
        var (inicio, fin) = PeriodosAhorroReferencia.GetPeriodBounds(tipo, primero, segundo, hoy, desplazamiento);
        casos.Add([tipo, primero, segundo, F(hoy), desplazamiento, F(inicio), F(fin)]);
    }

    Guardar("periodos.json", new { casos });
}

// ---------------------------------------------------------------------------
// 8. Ahorro: presupuesto, salud, promedio y porcentajes por categoria
// ---------------------------------------------------------------------------
{
    var azar = new Random(80);
    var salud = new List<object[]>();
    for (var n = 0; n < 3000; n++)
    {
        var sueldo = azar.Next(0, 8) == 0 ? 0m : Math.Round(azar.Next(100_000, 8_000_000) / 100m, 2);
        var porcentaje = azar.Next(0, 6) switch
        {
            0 => 0m,
            1 => 100m,
            _ => Math.Round(azar.Next(0, 10_000) / 100m, 2),
        };
        var total = azar.Next(0, 6) == 0 ? 0m : Math.Round(azar.Next(0, 9_000_000) / 100m, 2);
        var dias = azar.Next(0, 32);

        var r = PeriodosAhorroReferencia.Salud(sueldo, porcentaje, total, dias);
        salud.Add([D(sueldo), D(porcentaje), D(total), dias, D(r.MaxTotalSpending), D(r.Percent), D(r.ProgressPercent), r.HealthText, r.HealthColor, D(r.RemainingBudget), D(r.DailyAverage), D(r.SavedOrExceeded), r.Exceeded]);
    }

    // Empates exactos en el redondeo, que es donde HALF_EVEN y HALF_UP se separan.
    foreach (var (sueldo, total, dias) in new[] { (1000m, 0.125m, 1), (1000m, 0.135m, 1), (100m, 0.0125m, 1), (10m, 0.25m, 2), (10m, 0.35m, 2), (8m, 1m, 8), (3m, 0.05m, 2) })
    {
        var r = PeriodosAhorroReferencia.Salud(sueldo, 0m, total, dias);
        salud.Add([D(sueldo), D(0m), D(total), dias, D(r.MaxTotalSpending), D(r.Percent), D(r.ProgressPercent), r.HealthText, r.HealthColor, D(r.RemainingBudget), D(r.DailyAverage), D(r.SavedOrExceeded), r.Exceeded]);
    }

    var categorias = new List<object[]>();
    for (var n = 0; n < 600; n++)
    {
        var montos = Enumerable.Range(0, azar.Next(1, 9)).Select(_ => Monto(azar)).ToList();
        categorias.Add([montos.Select(D).ToList(), PeriodosAhorroReferencia.PorcentajesCategorias(montos).Select(D).ToList()]);
    }
    foreach (var montos in new[] { new[] { 1m, 1999m }, new[] { 1m, 7m }, new[] { 5m, 5m, 5m }, new[] { 0.5m, 399.5m }, new[] { 1m, 1m, 1m, 1m, 1m, 1m, 1m, 1m } })
        categorias.Add([montos.Select(D).ToList(), PeriodosAhorroReferencia.PorcentajesCategorias(montos.ToList()).Select(D).ToList()]);

    Guardar("ahorro.json", new { salud, categorias });
}

// ---------------------------------------------------------------------------
// 9. Mensualidad MSI y porcentaje de ahorro del perfil
// ---------------------------------------------------------------------------
{
    var azar = new Random(9);
    var plazos = new[] { 0, 1, 3, 6, 9, 12, 18, 24 };

    var msi = new List<object[]>();
    for (var n = 0; n < 2000; n++)
    {
        var monto = Monto(azar);
        var plazo = plazos[azar.Next(plazos.Length)];
        msi.Add([D(monto), plazo, D(PeriodosAhorroReferencia.MensualidadMsi(monto, plazo))]);
    }
    foreach (var (monto, plazo) in new[] { (0.25m, 2), (0.35m, 2), (0.05m, 2), (100m, 3), (200m, 3), (1000m, 6), (0.15m, 2), (2.5m, 4) })
        msi.Add([D(monto), plazo, D(PeriodosAhorroReferencia.MensualidadMsi(monto, plazo))]);

    var porcentaje = new List<object[]>();
    var monto2 = new List<object[]>();
    for (var n = 0; n < 2000; n++)
    {
        var sueldo = azar.Next(0, 10) == 0 ? 0m : Monto(azar) * 10;
        var cantidad = Monto(azar);
        porcentaje.Add([D(cantidad), D(sueldo), D(PeriodosAhorroReferencia.PorcentajeDesdeMonto(cantidad, sueldo))]);

        var pct = Math.Round(azar.Next(0, 10_000) / 100m, 2);
        monto2.Add([D(sueldo), D(pct), D(PeriodosAhorroReferencia.MontoDesdePorcentaje(sueldo, pct))]);
    }

    Guardar("msi_perfil.json", new { msi, porcentaje, monto = monto2 });
}

Console.WriteLine($"Fixtures escritos en {Path.GetFullPath(salida)}");
