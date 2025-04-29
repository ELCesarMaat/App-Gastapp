using Gastapp.Data;
using Gastapp.Models;

public static class DatabaseSeeder
{
    public static void SeedIncomeTypes(GastappDbContext context)
    {
        if (!context.IncomeTypes.Any())
        {
            context.IncomeTypes.AddRange(
                new IncomeType { IncomeTypeId = 1, IncomeTypeName = "Semanal" },
                new IncomeType { IncomeTypeId = 2, IncomeTypeName = "Quincenal" },
                new IncomeType { IncomeTypeId = 3, IncomeTypeName = "Mensual" }
            );
            context.SaveChanges();
        }
    }
}
